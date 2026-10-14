# Running CodeArena on a single VM

This is how to run the full stack on one Linux VM with Docker. It has **not** been deployed anywhere; the default
way to show the project is the recorded demo (see `PROJECT.md` section 16). Treat this as a checklist for an
optional, short-lived demo machine.

## Read this first: the Docker socket

The worker mounts `/var/run/docker.sock` so it can start one sandbox container per run. Access to that socket is
root on the VM: anything that controls the worker process can start a privileged container and mount the host's
filesystem. On a public machine that means:

- A remote code execution bug in the worker (or in a library it uses) is a full compromise of the VM, not of one
  container.
- Everything else on the VM (the database, `.env`, any other service) is exposed by that one bug.

So: use a VM that holds nothing else, with throwaway secrets, for a limited time, and destroy it afterwards.
`judge-images/README.md` lists the mitigations already in place and the production alternatives (gVisor,
Firecracker microVMs, dedicated judge hosts with no data on them).

## Requirements

- A Linux VM with cgroup v2 (Ubuntu 24.04 or newer works), 4 vCPU and 8 GB RAM or more. The load tests in
  `loadtest/results/` were run with 4 vCPU and 6 GiB for the whole stack, judging included.
- Docker Engine with the Compose plugin (`docker compose version`), installed from Docker's repository.
- Swap disabled or accounted for: the sandboxes set `memory-swap = memory`, so the memory limits only hold if the
  kernel enforces them (cgroup v2 does).

## Steps

```sh
git clone <your fork> codearena && cd codearena
cp .env.example .env
```

Edit `.env`:

- Replace every `change-me` value with a long random string (`openssl rand -base64 36`), including
  `GRAFANA_ADMIN_PASSWORD` and `ADMIN_PASSWORD`.
- `DOCKER_SOCKET_GID=$(stat -c %g /var/run/docker.sock)`.
- `CORS_ALLOWED_ORIGINS` must be the exact origin users type, for example `https://demo.example.com`.
- For the tutor, either point `OLLAMA_BASE_URL` at an Ollama instance or set `AI_CHAT_PROVIDER=openai` with a key.
  With neither, the tutor answers 503 and everything else works.

Build the judge images, then start everything:

```sh
docker compose --profile judge-images build judge-java judge-python
docker compose up -d --build --scale worker=3
docker compose ps
```

All services should show `healthy`. Then open the site and log in with the admin account from `.env`.

## TLS

The frontend container listens on plain HTTP (port `FRONTEND_PORT`, default 8080). For anything reachable from the
internet put a TLS-terminating reverse proxy in front of it (Caddy or nginx with Let's Encrypt) on 443, and publish
the frontend on `127.0.0.1` only (`ports: ["127.0.0.1:8080:8080"]` in an override file). Two consequences:

- Add the proxy's address to the api's trusted proxies, otherwise every request is attributed to the proxy and all
  users share one per-IP rate-limit bucket. In Compose the api trusts only the frontend container
  (`172.29.80.200`), and nginx appends the proxy's address to `X-Forwarded-For`. Set
  `RATE_LIMIT_TRUSTED_PROXIES: "172.29.80.200,<proxy address as nginx sees it>"` for the `api` service in an
  override file.
- WebSockets must be proxied too (`/ws`, with the `Upgrade` and `Connection` headers).

## Firewall checklist

- [ ] Inbound allowed: 22 (SSH, preferably from your IP only) and 443 (or 80 for a plain-HTTP throwaway demo).
- [ ] Inbound denied: everything else. Check with `ss -tlnp` and an external port scan.
- [ ] Prometheus (9090) and Grafana (3000) stay bound to `127.0.0.1`; reach them through an SSH tunnel
      (`ssh -L 3000:127.0.0.1:3000 vm`), never by opening the ports.
- [ ] Postgres, Redis, RabbitMQ and the management ports (9080, 9081) are not published at all; do not use
      `docker-compose.dev.yml` on the VM.
- [ ] Docker publishes ports by writing iptables rules that bypass `ufw`. Anything in a `ports:` list is reachable
      even if `ufw` denies it, so control exposure in the Compose files, or filter in the `DOCKER-USER` chain or the
      cloud provider's firewall.
- [ ] The Docker daemon is not listening on TCP (no `-H tcp://` in the daemon config).
- [ ] SSH: key-only login, no root login.
- [ ] Outbound: the sandboxes have no network, but the VM does. If the provider allows it, restrict outbound traffic
      to what the stack needs (package mirrors, the AI provider).

## Operating it

- Logs: `docker compose logs -f api worker`. Source code, tokens and prompts are not logged.
- Metrics: Grafana dashboard "CodeArena overview" through the SSH tunnel.
- Updates: `git pull && docker compose up -d --build --scale worker=3`. Flyway migrates the database on api start.
- Backups: `docker compose exec postgres pg_dump -U codearena codearena > backup.sql`. The Redis leaderboard is
  derived data and is rebuilt from Postgres.
- Shutdown: `docker compose down` keeps the data volumes; `docker compose down -v` deletes them. Destroy the VM when
  the demo is over.
