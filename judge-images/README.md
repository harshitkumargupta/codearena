# Judge images

Build both images (tags `codearena/judge-java:latest` and `codearena/judge-python:latest`) into the Docker daemon
the worker talks to. With Compose (from the repo root, `.env` present):

```sh
docker compose --profile judge-images build judge-java judge-python
```

or without Compose:

```sh
./judge-images/build.sh
```

The `judge-java` and `judge-python` services exist only to build these images; they sit behind the `judge-images`
profile, so a plain `docker compose up` never starts them. The worker starts sandboxes from the images by tag, so
they must exist in the same daemon before the first submission, or every job ends as INTERNAL_ERROR after its retries.

Both images run as the non-root user `judge` (uid/gid 10001). The worker supplies all runtime limits
(network, memory, CPU, pids, read-only rootfs, tmpfs, dropped capabilities, no-new-privileges), so the images
contain only the toolchain, the user, and two directories:

- `/input`: root-owned, mode 0755. The worker copies source, compiled classes and `stdin.txt` into a fresh
  anonymous volume mounted here, so the program can read it but not write to it.
- `/out` (Java only): owned by `judge`, used as the `javac` output volume in the compile container.

`judge-run` is the launcher for every run. It feeds `/input/stdin.txt` to the program, then appends a
`@@judge-meta <memory.peak bytes> <cpu usage_usec>` line to stderr from the container's cgroup. The worker
strips that line and reports the values as peak memory and CPU time.

## Docker host (Colima)

The worker connects to the daemon in `DOCKER_HOST` and defaults to `unix:///var/run/docker.sock`.
Colima does not create that socket by default, so set it before running the worker or its tests:

```sh
export DOCKER_HOST="unix://$HOME/.colima/default/docker.sock"
```

`docker context inspect colima --format '{{.Endpoints.docker.Host}}'` prints the exact path. The sandbox
tests skip with a message when the daemon is unreachable or the images are missing.

The RabbitMQ Testcontainers test also needs the socket path as seen inside the Colima VM:

```sh
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## The Docker socket: trade-off and mitigations

In Compose, the worker container mounts `/var/run/docker.sock` and joins the socket's group (`DOCKER_SOCKET_GID`
in `.env`, passed as `group_add`), because it creates one sandbox container per compile and test-case run. It is
the only service with the socket.

**Why this is a privilege risk.** The Docker API has no permission levels: whoever can talk to the socket can start
a privileged container, bind-mount the host's `/`, and so gets root on the Docker host. The worker itself runs as a
non-root user, but that does not matter for the socket; membership of its group is equivalent to root on the host.
So the security boundary is the worker process, not the container it runs in. If an attacker gains code execution
inside the worker (for example through a deserialization bug in a job message or a flaw in docker-java), they own the
host. User code in the sandboxes is not exposed to the socket: sandboxes do not mount it and have no network.

**Mitigations in place**
- Only the worker mounts the socket; the api, frontend and infrastructure do not.
- The worker publishes no ports and has no inbound HTTP API; its management port is reachable only on the Compose
  network. Its only input is `judge.jobs`, which only the api publishes to (RabbitMQ credentials from `.env`).
- The worker container runs as uid 10002 with a read-only root filesystem, `no-new-privileges` and all
  capabilities dropped.
- Job messages are deserialized only into the `com.codearena.common` record types (trusted-package allow-list).
- Every sandbox runs with the limits in PROJECT.md section 5: no network, memory and pids limits, read-only rootfs,
  all capabilities dropped, `no-new-privileges`, non-root user, wall-clock kill. Source is copied in with
  `copyArchiveToContainer`, never placed in a shell string or a host bind mount.
- The worker labels its sandboxes and a janitor removes any that outlive the stale age.

**Production alternatives**
- **gVisor** (`runsc` runtime): run sandboxes under a user-space kernel, so a container escape lands in gVisor rather
  than the host kernel. It reduces the risk from user code, but the worker would still need a way to start
  containers.
- **Firecracker microVMs** (for example via firecracker-containerd or Kata Containers): each run gets its own
  lightweight VM with a separate kernel. This is stronger isolation for untrusted code, at the cost of more
  operational complexity.
- **Dedicated judge hosts**: run workers on separate machines (or VMs) that hold no data or secrets except queue
  credentials, so a worker compromise does not reach the database or the api. Combine this with a narrow
  launcher in place of the raw socket, such as rootless Docker per host, or a small service that exposes only
  "run this image with these fixed limits".
