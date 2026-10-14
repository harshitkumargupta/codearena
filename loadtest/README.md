# Load tests

k6 scenarios plus the scripts that set up test users, sample resources, check correctness and clean up.
All numbers below come from the result files in `results/`, each of which records its own run conditions.

## Run conditions (all runs, 2026-10-14)

- Host: Mac17,5 (Apple A18 Pro, 6 cores, 8 GB RAM) on AC power, `caffeinate -dims` running.
- Colima 0.10.3 (vz) with **4 vCPU / 6 GiB**; the whole Compose stack, the judge sandboxes, Prometheus and Grafana
  share that VM. Other Docker workloads on the machine (SentinelAI) were stopped during the runs.
- k6 v2.3.0 on the host, against the frontend container (`http://localhost:8080`), so every request goes through
  nginx like a browser's.
- Workers run with `WORKER_CONCURRENCY=1` and prefetch 1: N workers judge at most N submissions at a time.
- Rate limits raised with `docker-compose.loadtest.yml` in every run except `real-limits`.

## Results

| Run | Load | Judged/min | k6 time to verdict p50 / p95 | Judge p50 / p95 (Prometheus) | Notes |
|---|---|---|---|---|---|
| [submissions-1-worker](results/submissions-1-worker.md) | closed, 6 VUs, 3 min steady | 66.3 | 4,343 / 5,854 ms | 878 / 1,573 ms | queue 3 to 5: the worker is saturated |
| [submissions-3-workers](results/submissions-3-workers.md) | closed, 6 VUs, 3 min steady | 90.3 | 2,888 / 4,604 ms | 1,938 / 3,244 ms | 1.36x the 1-worker throughput |
| [saturation-3-workers](results/saturation-3-workers.md) | open, 40 to 160/min in 2 min steps | 97.5 at 100/min offered | 7,075 / 10,190 ms at 100/min | 1,890 / 3,034 ms at 100/min | queue stops draining at 130/min; 3 findings |
| [saturation-130-rerun](results/saturation-130-rerun.md) | open, 130/min for 2 min, 3 s polling | 78.5 | 21,077 / 60,271 ms | 2,079 / 4,205 ms | full rate offered; queue 2 to 80 |
| [leaderboard-fanout](results/leaderboard-fanout.md) | 50 STOMP clients + 4 submitters, 3 min | n/a | 1,857 / 3,160 ms (all) | n/a | 5,000 of 5,000 boards delivered; broadcast to receipt p50 20 ms, p95 58 ms |
| [real-limits](results/real-limits.md) | closed, 4 VUs, 2 users, no think time | 19 in 1 min (the limit) | n/a | n/a | 35,097 x 429; 21,139 x 502 from proxy port exhaustion (finding) |

Every run passed the correctness checks except `saturation-3-workers`, which found one wrong verdict (finding 1
below): every submission finalized exactly once, none left PENDING or RUNNING, no INTERNAL_ERROR, and the Prometheus
verdict counters equal to the database counts.

### What the numbers say

- **Scaling 1 to 3 workers is far from linear on this machine**: 66.3 to 90.3 judged/min (1.36x) in the closed
  model. Judge time per submission doubled (p50 878 ms to 1,938 ms) while VM CPU went from 37% to 78% busy. In the
  3-worker run, 6 VUs with 1 s think time also limited the offered load (queue only 1 to 2), so the saturation run
  measures the real ceiling.
- **Saturation**: about 100 submissions/min with 3 workers. At 100/min offered the queue drains (97.5 judged/min);
  at 130/min it grows by about 40 per minute, and with a permanent backlog the 3 workers sustain about 80/min.
- **Bottleneck: the container lifecycle, not raw CPU.** VM CPU levels off at 76 to 82% busy with about 30% kernel
  time, `dockerd` + `containerd` use about 75% of one core, and with all 3 workers busy only 0.8 sandbox containers
  are running on average. Each submission costs one compile container plus one container per test case, and most
  of the judging time is spent creating, copying into, starting and removing them.
- **Fan-out is cheap**: 50 clients received every one of 100 boards, 20 ms (p50) after the api logged the
  broadcast; a result reached the boards 827 ms (p50) after it was applied, most of which is the deliberate 1 s
  coalescing.
- **Run-to-run variance is real**: an earlier pair of the 1-vs-3 worker runs (run ids 10140842 and 10140837,
  same settings, replaced only because the resource sampler did not yet record kernel time) measured 50.7 and 76.3
  judged/min. The ratio was similar (1.5x); the absolute numbers moved by about 30%. They were the first runs after
  the stack started, which may have played a part, but this was not established. Quote the result files' numbers
  with this caveat.

### Findings (not fixed during the measurements; proposed fixes in `PROJECT.md` section 16)

1. **A correct solution was judged TIME_LIMIT_EXCEEDED under contention** (`saturation-3-workers.md`):
   submission 1773, the reference Java Two Sum, took 2,286 ms against the 2,000 ms limit right after a 20 s stall;
   the other 93 runs of it took at most 984 ms. The limit is on wall time, so CPU contention counts against the
   program.
2. **The sweeper re-publishes jobs that are only waiting in a long queue** (`saturation-3-workers.md`): 59
   submissions were judged twice while a 174-job backlog drained; the api ignored all 59 second results, so
   correctness held, but the work was wasted when the system was already behind.
3. **nginx runs out of local ports at high request rates** (`real-limits.md`): about 560 requests/s of
   immediately rejected submits produced 21,139 `502` responses, because nginx opens a new connection to the api
   for every request.
4. **Harness limits**: in `saturation-3-workers.md` k6 dropped 122 iterations at 130 and 160/min (VU allocation),
   so those steps offered 116 and 114.5/min; the tables use the measured rate, and `saturation-130-rerun.md`
   repeats the step at the full rate. The fan-out file's original "broadcast to receipt" maximum (2,853 ms) was a
   matching artifact, corrected in the same file.

## How to run

Prerequisites: k6 (`brew install k6`), Python 3, and `npm ci` in `frontend/` (the screenshot uses its Playwright).
Start the stack with the raised limits and 3 workers, using an env file that has every required value:

```sh
docker compose --env-file .env -f docker-compose.yml -f docker-compose.loadtest.yml up -d --build --scale worker=3
```

Then, for example:

```sh
ENV_FILE=.env LABEL=submissions-3-workers TITLE="Submissions, 3 workers" KIND=submissions WORKERS=3 LIMITS=raised \
  SCRIPT=submissions.js USERS=12 VUS=6 WARMUP=1m STEADY=3m RAMPDOWN=30s THINK_S=1 SETTINGS="..." ./loadtest/run.sh
```

`run.sh` scales the workers, creates users named `lt<runid>_*` through the api (and a contest with `CONTEST=1`),
samples resources every 5 s, runs k6, waits until the run's submissions are final and the judge queues are empty,
takes a Grafana screenshot, and runs `analyze.py`, which writes `results/<LABEL>.md` and exits non-zero on any
failed correctness check. It then prints the rows matched by the run prefix and deletes them only if the checks
passed; otherwise the data is kept for inspection (`python3 loadtest/cleanup.py --run-id <id> --env-file .env`
shows the counts, `--yes` deletes).

Other settings: `KIND=saturation MODE=open RATES=40,70,100 STEP=2m PRE_VUS MAX_VUS POLL_INTERVAL_S`;
`KIND=leaderboard SCRIPT=leaderboard.js CONTEST=1 CLIENTS SUBMITTERS DURATION_S`; `LIMITS=real` uses the api as it is
(recreate it without the override first).

Raw per-run output (k6 console log, resource samples, user credentials) stays in `loadtest/.runs/`, which is not
committed. Credentials are generated per run and never printed.
