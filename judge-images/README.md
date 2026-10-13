# Judge images

Build both images (tags `codearena/judge-java:latest` and `codearena/judge-python:latest`):

```sh
./judge-images/build.sh
```

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
