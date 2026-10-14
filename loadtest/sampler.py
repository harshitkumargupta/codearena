"""Samples resource use every few seconds into a CSV until stopped (SIGTERM/SIGINT).

- vm_busy_pct: whole Docker VM CPU from /proc/stat (not namespaced, so read through any running container).
- top_*: per-process CPU in the Colima VM (colima ssh top), summed by command, in % of one core. top only sees
  processes alive at both of its samples, so it misses short-lived sandbox processes.
- vm_user/system/iowait_pct: the same counters split; container start-up and teardown shows up as system time.
- sandboxes_alive: judge containers (label codearena.sandbox) existing at sample time.
- cpu_* / mem_*: docker stats for the CodeArena services (sandboxes live too briefly for docker stats).
"""
import argparse
import csv
import shutil
import signal
import subprocess
import time

STOP = False
TOP_GROUPS = {"java": "java", "dockerd": "dockerd", "containerd": "containerd", "containerd-shim": "containerd",
              "runc": "runc", "python3": "python", "python": "python", "postgres": "postgres",
              "beam.smp": "rabbitmq", "redis-server": "redis", "nginx": "nginx"}


def stop(*_):
    global STOP
    STOP = True


def proc_stat(container):
    out = subprocess.run(["docker", "exec", container, "cat", "/proc/stat"], capture_output=True, text=True).stdout
    f = [int(x) for x in out.splitlines()[0].split()[1:]]
    return {"total": sum(f), "idle": f[3], "iowait": f[4], "user": f[0] + f[1], "system": f[2] + f[5] + f[6]}


def top_by_command():
    if not shutil.which("colima"):
        return {}
    out = subprocess.run(["colima", "ssh", "--", "top", "-b", "-n", "2", "-d", "1", "-w", "200"],
                         capture_output=True, text=True).stdout
    blocks = out.split("top - ")
    groups = {}
    in_table = False
    for line in blocks[-1].splitlines():
        parts = line.split()
        if parts[:1] == ["PID"]:
            in_table = True
            continue
        if in_table and len(parts) >= 12:
            group = TOP_GROUPS.get(parts[11])
            if group:
                try:
                    groups[group] = groups.get(group, 0.0) + float(parts[8])
                except ValueError:
                    pass
    return groups


def docker_stats():
    out = subprocess.run(["docker", "stats", "--no-stream", "--format", "{{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"],
                         capture_output=True, text=True).stdout
    cpu, mem = {}, {}
    for line in out.splitlines():
        name, pct, usage = line.split("\t")
        if not name.startswith("codearena-"):
            continue
        key = name.replace("codearena-", "").rsplit("-", 1)[0]
        try:
            cpu[key] = cpu.get(key, 0.0) + float(pct.rstrip("%"))
        except ValueError:
            continue
        used = usage.split("/")[0].strip()
        factor = {"KiB": 1 / 1024, "MiB": 1, "GiB": 1024, "B": 1 / 1048576}
        for unit, f in sorted(factor.items(), key=lambda kv: -len(kv[0])):
            if used.endswith(unit):
                try:
                    mem[key] = mem.get(key, 0.0) + float(used[: -len(unit)]) * f
                except ValueError:
                    pass
                break
    return cpu, mem


def sandboxes_alive():
    out = subprocess.run(["docker", "ps", "-q", "--filter", "label=codearena.sandbox=true"], capture_output=True,
                         text=True).stdout
    return len(out.split())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", required=True)
    ap.add_argument("--interval", type=float, default=5)
    ap.add_argument("--stat-container", default="codearena-redis-1")
    args = ap.parse_args()
    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)
    services = ["api", "worker", "postgres", "rabbitmq", "redis", "frontend", "prometheus", "grafana"]
    tops = sorted(set(TOP_GROUPS.values()))
    with open(args.out, "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["ts", "vm_busy_pct", "vm_user_pct", "vm_system_pct", "vm_iowait_pct", "sandboxes_alive"]
                   + [f"top_{t}" for t in tops] + [f"cpu_{s}" for s in services]
                   + [f"mem_mib_{s}" for s in services])
        prev = proc_stat(args.stat_container)
        while not STOP:
            started = time.time()
            try:
                groups = top_by_command()
                cpu, mem = docker_stats()
                cur = proc_stat(args.stat_container)
            except (ValueError, IndexError) as ex:
                print(f"sampler: skipped a sample ({ex})", flush=True)
                time.sleep(args.interval)
                continue
            alive = sandboxes_alive()
            d = {k: cur[k] - prev[k] for k in cur}
            prev = cur
            share = lambda k: 100.0 * d[k] / d["total"] if d["total"] else 0.0
            busy = 100.0 - share("idle") - share("iowait")
            w.writerow([f"{time.time():.1f}", f"{busy:.1f}", f"{share('user'):.1f}", f"{share('system'):.1f}",
                        f"{share('iowait'):.1f}", alive] + [f"{groups.get(t, 0.0):.1f}" for t in tops]
                       + [f"{cpu.get(s, 0.0):.1f}" for s in services] + [f"{mem.get(s, 0.0):.0f}" for s in services])
            f.flush()
            time.sleep(max(0.0, args.interval - (time.time() - started)))


if __name__ == "__main__":
    main()
