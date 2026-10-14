"""Builds loadtest/results/<label>.md for one run from the k6 summary, Prometheus, Postgres, the api/worker logs and
the resource samples, and runs the correctness checks. Exits 1 when a check fails (cleanup must not run then)."""
import argparse
import csv
import datetime
import json
import os
import re
import shutil
import statistics
import subprocess
import time
import urllib.parse
import urllib.request

from common import REPO, compose, like_prefix, psql

PROM = "http://localhost:9090/api/v1/"
CONDITIONS = "SentinelAI stopped, Colima 4 CPU / 6 GiB, on AC power"


def prom(path, **params):
    url = PROM + path + "?" + urllib.parse.urlencode(params)
    with urllib.request.urlopen(url, timeout=30) as res:
        return json.load(res)["data"]["result"]


def instant(expr, t):
    return prom("query", query=expr, time=f"{t:.3f}")


def scalar(expr, t):
    r = instant(expr, t)
    if not r:
        return None
    v = float(r[0]["value"][1])
    return None if v != v else v


def iso(t):
    return datetime.datetime.fromtimestamp(t, datetime.timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")


def fmt_ms(v):
    return "n/a" if v is None else f"{v:,.0f} ms"


def fmt_s(v):
    return "n/a" if v is None else f"{v * 1000:,.0f} ms"


def k6_metric(summary, name, stat):
    m = summary.get("metrics", {}).get(name)
    if not m:
        return None
    return m.get("values", m).get(stat)


def pct(values, q):
    if not values:
        return None
    s = sorted(values)
    k = (len(s) - 1) * q
    lo, hi = int(k), min(int(k) + 1, len(s) - 1)
    return s[lo] + (s[hi] - s[lo]) * (k - lo)


def clock_offset_ms():
    """Docker VM clock minus host clock, from three round trips."""
    samples = []
    for _ in range(3):
        before = time.time() * 1000
        out = subprocess.run(["docker", "exec", "codearena-redis-1", "date", "+%s%N"], capture_output=True,
                             text=True).stdout.strip()
        after = time.time() * 1000
        samples.append(int(out) / 1e6 - (before + after) / 2)
    return statistics.median(samples)


def log_lines(env_file, service, since):
    res = compose(env_file, "logs", "--no-color", "--no-log-prefix", "-t", "--since", since, service)
    return res.stdout.splitlines()


def parse_log_ts(line):
    ts = line.split(" ", 1)[0]
    return datetime.datetime.fromisoformat(ts[:26].rstrip("Z") + "+00:00").timestamp()


def samples_summary(path, start, end):
    if not path or not os.path.exists(path):
        return None
    with open(path) as f:
        rows = [r for r in csv.DictReader(f) if start <= float(r["ts"]) <= end]
    if not rows:
        return None
    cols = [c for c in rows[0] if c != "ts"]
    out = {}
    for c in cols:
        vals = [float(r[c]) for r in rows]
        out[c] = (statistics.mean(vals), max(vals))
    out["_n"] = len(rows)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run-id", required=True)
    ap.add_argument("--label", required=True)
    ap.add_argument("--title", required=True)
    ap.add_argument("--kind", choices=["submissions", "saturation", "leaderboard", "limits"], required=True)
    ap.add_argument("--workers", type=int, required=True)
    ap.add_argument("--limits", choices=["raised", "real"], required=True)
    ap.add_argument("--settings", required=True, help="k6 settings line for the results file")
    ap.add_argument("--start", type=float, required=True)
    ap.add_argument("--end", type=float, required=True)
    ap.add_argument("--steady-start", type=float)
    ap.add_argument("--steady-end", type=float)
    ap.add_argument("--steps", help="saturation: comma-separated rate:start:end")
    ap.add_argument("--env-file", default=os.path.join(REPO, ".env"))
    ap.add_argument("--summary", required=True)
    ap.add_argument("--samples")
    ap.add_argument("--console")
    ap.add_argument("--screenshot")
    args = ap.parse_args()

    results = os.path.join(REPO, "loadtest", "results")
    os.makedirs(results, exist_ok=True)
    with open(args.summary) as f:
        summary = json.load(f)
    shutil.copy(args.summary, os.path.join(results, f"{args.label}.k6-summary.json"))

    now = time.time()
    pattern = like_prefix(args.run_id)
    findings = []
    lines = []
    w = lines.append

    # ---------------------------------------------------------------- database facts for the run's users
    by_user = f"from submissions s join users u on u.id = s.user_id where u.username like '{pattern}' escape '\\'"
    db_counts = {(lang.lower(), status.lower()): int(n) for lang, status, n in psql(
        args.env_file, f"select s.language, s.status, count(*) {by_user} group by 1, 2")}
    total = sum(db_counts.values())
    open_count = sum(n for (l, s), n in db_counts.items() if s in ("pending", "running"))
    internal = sum(n for (l, s), n in db_counts.items() if s == "internal_error")
    ids = [int(r[0]) for r in psql(args.env_file, f"select s.id {by_user}")]

    # ---------------------------------------------------------------- exactly-once from the api log
    since = datetime.datetime.fromtimestamp(args.start - 60, datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    api_log = log_lines(args.env_file, "api", since)
    worker_log = log_lines(args.env_file, "worker", since)
    finalized, ignored = {}, 0
    for line in api_log:
        m = re.search(r"Submission (\d+) judged ([A-Z_]+)", line)
        if m:
            finalized[int(m.group(1))] = finalized.get(int(m.group(1)), 0) + 1
        if "already final" in line:
            ignored += 1
    judged = {}
    for line in worker_log:
        m = re.search(r"Judging submission (\d+) \(attempt", line)
        if m:
            judged[int(m.group(1))] = judged.get(int(m.group(1)), 0) + 1
    id_set = set(ids)
    not_once = [i for i in ids if finalized.get(i, 0) != 1]
    rejudged = sum(1 for i in ids if judged.get(i, 0) > 1)
    extra_judgings = sum(judged.get(i, 0) - 1 for i in ids if judged.get(i, 0) > 1)

    # ---------------------------------------------------------------- Prometheus counters vs database
    t0, t1 = args.start - 2, now
    def counters(t):
        return {(r["metric"]["language"], r["metric"]["verdict"]): float(r["value"][1]) for r in instant(
            "sum by (language, verdict) (codearena_submission_verdicts_total)", t)}
    c0, c1 = counters(t0), counters(t1)
    prom_delta = {k: round(c1.get(k, 0) - c0.get(k, 0)) for k in set(c0) | set(c1)}
    prom_delta = {k: v for k, v in prom_delta.items() if v}
    db_final = {k: v for k, v in db_counts.items() if k[1] not in ("pending", "running")}
    api_restarted = scalar("max(process_start_time_seconds{job=\"api\"})", t1)
    restarted = api_restarted is not None and api_restarted > t0

    if open_count:
        findings.append(f"{open_count} submission(s) still PENDING/RUNNING after the drain")
    if internal:
        findings.append(f"{internal} submission(s) ended INTERNAL_ERROR")
    if not_once:
        findings.append(f"{len(not_once)} submission(s) not finalized exactly once in the api log (e.g. {not_once[:5]})")
    if restarted:
        findings.append("api restarted during the run; Prometheus counter comparison is not valid")
    elif prom_delta != db_final:
        findings.append(f"Prometheus verdict deltas {sorted(prom_delta.items())} differ from database {sorted(db_final.items())}")
    mism = k6_metric(summary, "verdict_mismatch", "count") or 0
    if mism:
        findings.append(f"{mism:.0f} verdict(s) differed from the expected verdict of the submitted solution")

    # ---------------------------------------------------------------- header
    w(f"# {args.title}")
    w("")
    w(f"Run `{args.run_id}` ({args.label}), {iso(args.start)} to {iso(args.end)}.")
    w("")
    w("## Run conditions")
    w("")
    w(f"- {CONDITIONS}.")
    w("- Host: Mac17,5 (Apple A18 Pro, 6 cores, 8 GB RAM), macOS, Colima 0.10.3 (vz), Docker 29.")
    w(f"- Workers: {args.workers} (`WORKER_CONCURRENCY=1`, prefetch 1), api x1, everything in Docker Compose on the "
      "same VM as the sandboxes.")
    if args.limits == "raised":
        w("- **Rate limits raised** with `docker-compose.loadtest.yml` (all api limits set to 100000); k6 runs from "
          "one client IP and a few dozen users, so the real limits would block it.")
    else:
        w("- **Real rate limits** (the defaults from `.env.example`: 10 submissions/min per user).")
    w(f"- k6 v2.3.0 on the host, against the frontend container (`http://localhost:8080`). {args.settings}")
    poll = os.environ.get("POLL_INTERVAL_S", "0.25")
    w("- Time to verdict is measured by k6 from just before `POST /api/submissions` until a `GET /api/submissions/{id}` "
      f"poll (every {float(poll) * 1000:.0f} ms) sees a final status, so it includes up to {float(poll) * 1000:.0f} ms "
      "of polling granularity.")
    w("")

    # ---------------------------------------------------------------- k6 results
    w("## k6")
    w("")
    reqs = k6_metric(summary, "http_reqs", "count")
    failed = k6_metric(summary, "http_req_failed", "rate")
    ok = k6_metric(summary, "submissions_ok", "count") or 0
    rej = k6_metric(summary, "submit_rejected", "count") or 0
    tmo = k6_metric(summary, "verdict_timeout", "count") or 0
    w("| Metric | Value |")
    w("|---|---|")
    w(f"| Submissions accepted by the api (201) | {ok:.0f} |")
    w(f"| Submissions rejected | {rej:.0f} |")
    w(f"| Submit success rate | {100 * ok / (ok + rej):.2f}% |" if ok + rej else "| Submit success rate | n/a |")
    w(f"| HTTP requests (incl. polls) / failed rate | {reqs:.0f} / {100 * (failed or 0):.2f}% |")
    w(f"| Verdict timeouts (no final status within 10 min) | {tmo:.0f} |")
    w(f"| Verdict differed from the solution's expected verdict | {mism:.0f} |")
    w(f"| Submit latency p50 / p95 (all) | {fmt_ms(k6_metric(summary, 'http_req_duration{name:submit}', 'p(50)'))} / "
      f"{fmt_ms(k6_metric(summary, 'http_req_duration{name:submit}', 'p(95)'))} |")
    w(f"| Time to verdict p50 / p95 (all) | {fmt_ms(k6_metric(summary, 'time_to_verdict', 'p(50)'))} / "
      f"{fmt_ms(k6_metric(summary, 'time_to_verdict', 'p(95)'))} |")
    if args.kind == "submissions":
        w(f"| Submit latency p50 / p95 (steady state) | "
          f"{fmt_ms(k6_metric(summary, 'http_req_duration{name:submit,phase:steady}', 'p(50)'))} / "
          f"{fmt_ms(k6_metric(summary, 'http_req_duration{name:submit,phase:steady}', 'p(95)'))} |")
        w(f"| Time to verdict p50 / p95 (steady state) | "
          f"{fmt_ms(k6_metric(summary, 'time_to_verdict{phase:steady}', 'p(50)'))} / "
          f"{fmt_ms(k6_metric(summary, 'time_to_verdict{phase:steady}', 'p(95)'))} |")
    if args.kind == "limits":
        w(f"| Rejected with 429 Too Many Requests | {k6_metric(summary, 'submit_rejected{status:429}', 'count') or 0:.0f} |")
    w("")

    # ---------------------------------------------------------------- throughput and Prometheus
    def window_stats(a, b, label):
        minutes = (b - a) / 60
        created = int(psql(args.env_file, f"select count(*) {by_user} and s.created_at >= to_timestamp({a}) "
                                          f"and s.created_at < to_timestamp({b})")[0][0])
        done = int(psql(args.env_file, f"select count(*) {by_user} and s.judged_at >= to_timestamp({a}) "
                                       f"and s.judged_at < to_timestamp({b})")[0][0])
        rng = f"{int(b - a)}s"
        judge50 = scalar(f"histogram_quantile(0.5, sum by (le) (increase(codearena_judge_duration_seconds_bucket[{rng}])))", b)
        judge95 = scalar(f"histogram_quantile(0.95, sum by (le) (increase(codearena_judge_duration_seconds_bucket[{rng}])))", b)
        e2e50 = scalar(f"histogram_quantile(0.5, sum by (le) (increase(codearena_submission_e2e_latency_seconds_bucket[{rng}])))", b)
        e2e95 = scalar(f"histogram_quantile(0.95, sum by (le) (increase(codearena_submission_e2e_latency_seconds_bucket[{rng}])))", b)
        q_start = scalar('codearena_queue_depth{queue="judge.jobs"}', a + 15)
        q_end = scalar('codearena_queue_depth{queue="judge.jobs"}', b)
        q_max = scalar(f'max_over_time(codearena_queue_depth{{queue="judge.jobs"}}[{rng}])', b)
        return {"label": label, "minutes": minutes, "created_pm": created / minutes, "judged_pm": done / minutes,
                "judge50": judge50, "judge95": judge95, "e2e50": e2e50, "e2e95": e2e95,
                "q_start": q_start, "q_end": q_end, "q_max": q_max}

    windows = []
    if args.kind == "saturation" and args.steps:
        for step in args.steps.split(","):
            rate, a, b = step.split(":")
            windows.append(window_stats(float(a), float(b), f"{rate}/min offered"))
    elif args.steady_start:
        windows.append(window_stats(args.steady_start, args.steady_end, "steady state"))
    windows.append(window_stats(args.start, args.end, "whole run"))

    w("## Throughput and server-side latency")
    w("")
    w("Created and judged per minute come from `submissions.created_at` / `judged_at` in Postgres for this run's users. "
      "Judge and end-to-end percentiles are `histogram_quantile` over the window from the Prometheus histograms "
      "(bucket boundaries limit their precision). Queue depth is ready messages in `judge.jobs`, sampled every 15 s.")
    w("")
    head = "| Window | Minutes | Created/min | Judged/min | Judge p50 / p95 | End-to-end p50 / p95 | Queue start / end / max |"
    if args.kind == "saturation":
        head = head.replace("| Minutes |", "| Minutes | k6 time to verdict p50 / p95 |")
    w(head)
    w("|" + "---|" * (head.count("|") - 1))
    for win in windows:
        extra = ""
        if args.kind == "saturation":
            phase = "rate_" + win["label"].split("/")[0] if "offered" in win["label"] else None
            if phase:
                extra = (f" {fmt_ms(k6_metric(summary, f'time_to_verdict{{phase:{phase}}}', 'p(50)'))} / "
                         f"{fmt_ms(k6_metric(summary, f'time_to_verdict{{phase:{phase}}}', 'p(95)'))} |")
            else:
                extra = " |"
        q = lambda v: "n/a" if v is None else f"{v:.0f}"
        w(f"| {win['label']} | {win['minutes']:.1f} |{extra} {win['created_pm']:.1f} | {win['judged_pm']:.1f} | "
          f"{fmt_s(win['judge50'])} / {fmt_s(win['judge95'])} | {fmt_s(win['e2e50'])} / {fmt_s(win['e2e95'])} | "
          f"{q(win['q_start'])} / {q(win['q_end'])} / {q(win['q_max'])} |")
    w("")

    series = prom("query_range", query='codearena_queue_depth{queue="judge.jobs"}', start=f"{args.start:.0f}",
                  end=f"{now:.0f}", step="30")
    if series:
        points = [(float(t) - args.start, v) for t, v in series[0]["values"]]
        w("Queue depth over time (`judge.jobs`, every 30 s from the run start):")
        w("")
        w("```")
        w(" ".join(f"{int(t)}s={'?' if v == 'NaN' else int(float(v))}" for t, v in points))
        w("```")
        w("")

    # ---------------------------------------------------------------- resources
    span = (args.steady_start, args.steady_end) if args.steady_start else (args.start, args.end)
    res = samples_summary(args.samples, *span)
    if res:
        w("## Resources (mean / max over the " + ("steady state" if args.steady_start else "run") + f", {res['_n']} samples)")
        w("")
        w("VM CPU is the whole Docker VM from `/proc/stat` (4 vCPU = 100%), split into user and system (kernel) time. "
          "Process CPU is from `top` inside the VM in % of one core; it misses processes shorter than its 1 s "
          "interval, which includes most sandbox runs. Container CPU is `docker stats` (% of one core).")
        w("")
        w("| Measure | Mean | Max |")
        w("|---|---|---|")
        def row(name, key, unit=""):
            if key in res:
                w(f"| {name} | {res[key][0]:.0f}{unit} | {res[key][1]:.0f}{unit} |")
        row("VM CPU busy", "vm_busy_pct", "%")
        row("VM CPU user", "vm_user_pct", "%")
        row("VM CPU system (kernel)", "vm_system_pct", "%")
        row("VM CPU iowait", "vm_iowait_pct", "%")
        row("Sandbox containers alive", "sandboxes_alive")
        for proc in ["java", "python", "dockerd", "containerd", "runc", "postgres", "rabbitmq"]:
            row(f"top: {proc}", f"top_{proc}", "%")
        for svc in ["api", "worker", "postgres", "rabbitmq"]:
            row(f"docker stats CPU: {svc}", f"cpu_{svc}", "%")
        for svc in ["api", "worker"]:
            row(f"memory: {svc}", f"mem_mib_{svc}", " MiB")
        w("")

    # ---------------------------------------------------------------- leaderboard fan-out
    if args.kind == "leaderboard" and args.console:
        offset = clock_offset_ms()
        recv = []
        per_client = {}
        with open(args.console) as f:
            for line in f:
                m = re.search(r"LBRECV (\d+) (\d+)", line)
                if m:
                    t = int(m.group(2)) + offset
                    recv.append((int(m.group(1)), t))
                    per_client[int(m.group(1))] = per_client.get(int(m.group(1)), 0) + 1
        broadcasts = [parse_log_ts(l) * 1000 for l in api_log if "Broadcast leaderboard for contest" in l]
        broadcasts = [b for b in broadcasts if args.start * 1000 <= b <= args.end * 1000 + 120000]
        contest = psql(args.env_file, f"select id from contests where title like '{pattern}' escape '\\'")
        judged_at = []
        if contest:
            judged_at = sorted(float(r[0]) * 1000 for r in psql(
                args.env_file, f"select extract(epoch from judged_at) from submissions where contest_id = {contest[0][0]} "
                               "and judged_at is not null"))
        def lag(t, events):
            prior = [e for e in events if e <= t]
            return t - prior[-1] if prior else None
        from_judged = [x for x in (lag(t, judged_at) for _, t in recv) if x is not None]
        # The api logs a broadcast after handing it to the broker, so a receipt can precede its own log line.
        # Receipts are grouped into bursts (one per broadcast) and each burst is paired with the nearest log line.
        times = sorted(t for _, t in recv)
        bursts = []
        for t in times:
            if bursts and t - bursts[-1][-1] < 300:
                bursts[-1].append(t)
            else:
                bursts.append([t])
        from_broadcast = []
        for burst in bursts:
            near = [b for b in broadcasts if abs(burst[0] - b) < 1000]
            if near:
                own = min(near, key=lambda b: abs(burst[0] - b))
                from_broadcast.extend(t - own for t in burst)
        clients = int(k6_metric(summary, "lb_connected", "count") or 0)
        counts = sorted(per_client.values()) or [0]
        w("## Leaderboard fan-out")
        w("")
        w(f"Clock offset Docker VM minus host: {offset:.1f} ms (median of 3 round trips; receive times were shifted by it).")
        w("")
        w("| Metric | Value |")
        w("|---|---|")
        w(f"| STOMP clients connected | {clients} |")
        w(f"| Broadcasts logged by the api | {len(broadcasts)} |")
        w(f"| Boards received (all clients) | {len(recv)} |")
        w(f"| Boards per client min / median / max | {counts[0]} / {statistics.median(counts):.0f} / {counts[-1]} |")
        if broadcasts and clients:
            w(f"| Delivered / expected (broadcasts x clients) | {len(recv)} / {len(broadcasts) * clients} "
              f"({100 * len(recv) / (len(broadcasts) * clients):.1f}%) |")
        w(f"| Result applied (`judged_at`) to board received p50 / p95 / max | {fmt_ms(pct(from_judged, .5))} / "
          f"{fmt_ms(pct(from_judged, .95))} / {fmt_ms(max(from_judged) if from_judged else None)} |")
        w(f"| Broadcast log line to board received p50 / p95 / max | {fmt_ms(pct(from_broadcast, .5))} / "
          f"{fmt_ms(pct(from_broadcast, .95))} / {fmt_ms(max(from_broadcast) if from_broadcast else None)} |")
        w("")
        w("`judged_at` to receipt includes the coalescing window: the api broadcasts a contest's board at most once per "
          "`LEADERBOARD_BROADCAST_INTERVAL` (1 s), so up to 1 s of this is by design. Each receipt is matched to the "
          "latest result applied before it; for the broadcast row, each burst of receipts is paired with the nearest "
          "broadcast log line.")
        w("")

    # ---------------------------------------------------------------- correctness
    w("## Correctness checks")
    w("")
    w("| Check | Result |")
    w("|---|---|")
    w(f"| Submissions in the database for this run | {total} |")
    w(f"| Still PENDING / RUNNING after drain | {open_count} |")
    w(f"| INTERNAL_ERROR | {internal} |")
    w(f"| Finalized exactly once (one `Submission N judged` api log line each) | {len(ids) - len(not_once)} of {len(ids)} |")
    w(f"| Duplicate results ignored by the api (`already final`) | {ignored} |")
    w(f"| Submissions judged more than once by workers (extra judgings) | {rejudged} ({extra_judgings}) |")
    w(f"| Prometheus verdict counter deltas equal database counts | "
      f"{'n/a (api restarted)' if restarted else ('yes' if prom_delta == db_final else 'NO')} |")
    w("")
    w("Verdicts (database):")
    w("")
    w("| Language | Verdict | Database | Prometheus delta |")
    w("|---|---|---|---|")
    for key in sorted(set(db_final) | set(prom_delta)):
        w(f"| {key[0]} | {key[1]} | {db_final.get(key, 0)} | {prom_delta.get(key, 0)} |")
    w("")
    if findings:
        w("**Findings:**")
        w("")
        for f_ in findings:
            w(f"- {f_}")
    else:
        w("All checks passed.")
    w("")
    if args.screenshot:
        w(f"![Grafana during the run]({os.path.basename(args.screenshot)})")
        w("")

    out = os.path.join(results, f"{args.label}.md")
    with open(out, "w") as f:
        f.write("\n".join(lines))
    print(f"wrote {os.path.relpath(out, REPO)}; findings: {len(findings)}")
    for f_ in findings:
        print("  FINDING:", f_)
    raise SystemExit(1 if findings else 0)


if __name__ == "__main__":
    main()
