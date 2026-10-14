# Saturation search, 3 workers (open model, 40 to 160 submissions/min)

Run `10140910` (saturation-3-workers), 2026-10-14 09:11:09 UTC to 2026-10-14 09:22:05 UTC.

## Run conditions

- SentinelAI stopped, Colima 4 CPU / 6 GiB, on AC power.
- Host: Mac17,5 (Apple A18 Pro, 6 cores, 8 GB RAM), macOS, Colima 0.10.3 (vz), Docker 29.
- Workers: 3 (`WORKER_CONCURRENCY=1`, prefetch 1), api x1, everything in Docker Compose on the same VM as the sandboxes.
- **Rate limits raised** with `docker-compose.loadtest.yml` (all api limits set to 100000); k6 runs from one client IP and a few dozen users, so the real limits would block it.
- k6 v2.3.0 on the host, against the frontend container (`http://localhost:8080`). Scenario: ramping-arrival-rate (open model: new submissions arrive at the offered rate whether or not earlier ones finished), steps of 40, 70, 100, 130 and 160 submissions/min, 2 min each with a 5 s ramp between steps; 40 users; polling every 1 s (not 250 ms) so hundreds of waiting VUs do not load the api; same 8-solution mix.
- Time to verdict is measured by k6 from just before `POST /api/submissions` until a `GET /api/submissions/{id}` poll (every 1000 ms) sees a final status, so it includes up to 1000 ms of polling granularity.

## k6

| Metric | Value |
|---|---|
| Submissions accepted by the api (201) | 914 |
| Submissions rejected | 0 |
| Submit success rate | 100.00% |
| HTTP requests (incl. polls) / failed rate | 32019 / 0.00% |
| Verdict timeouts (no final status within 10 min) | 0 |
| Verdict differed from the solution's expected verdict | 1 |
| Submit latency p50 / p95 (all) | 15 ms / 66 ms |
| Time to verdict p50 / p95 (all) | 9,120 ms / 91,759 ms |

## Throughput and server-side latency

Created and judged per minute come from `submissions.created_at` / `judged_at` in Postgres for this run's users. Judge and end-to-end percentiles are `histogram_quantile` over the window from the Prometheus histograms (bucket boundaries limit their precision). Queue depth is ready messages in `judge.jobs`, sampled every 15 s.

| Window | Minutes | k6 time to verdict p50 / p95 | Created/min | Judged/min | Judge p50 / p95 | End-to-end p50 / p95 | Queue start / end / max |
|---|---|---|---|---|---|---|---|
| 40/min offered | 2.0 | 1,053 ms / 2,060 ms | 40.0 | 40.0 | 835 ms / 1,519 ms | 868 ms / 1,563 ms | 0 / 0 / 0 |
| 70/min offered | 2.0 | 2,022 ms / 14,154 ms | 70.0 | 64.5 | 964 ms / 5,166 ms | 1,029 ms / 13,683 ms | 0 / 13 / 13 |
| 100/min offered | 2.0 | 7,075 ms / 10,190 ms | 100.0 | 97.5 | 1,890 ms / 3,034 ms | 6,514 ms / 9,436 ms | 7 / 9 / 13 |
| 130/min offered | 2.0 | 41,369 ms / 66,776 ms | 116.0 | 81.0 | 2,147 ms / 3,591 ms | 32,413 ms / 47,044 ms | 15 / 77 / 77 |
| 160/min offered | 2.0 | 89,455 ms / 110,087 ms | 114.5 | 68.5 | 2,531 ms / 4,592 ms | 65,498 ms / 95,921 ms | 94 / 174 / 174 |
| whole run | 10.9 | | 83.6 | 69.8 | 1,859 ms / 3,896 ms | 9,186 ms / 91,626 ms | 0 / 154 / 174 |

Queue depth over time (`judge.jobs`, every 30 s from the run start):

```
0s=0 29s=0 59s=0 89s=0 119s=0 149s=0 179s=0 209s=0 239s=7 269s=7 299s=11 329s=9 359s=6 389s=15 419s=42 449s=59 479s=71 509s=85 539s=104 569s=126 599s=149 629s=174 659s=154 689s=112 719s=71 749s=59 779s=56 809s=20 839s=0 869s=0 899s=0 929s=0 959s=0 989s=0 1019s=0 1049s=0 1079s=0 1109s=0
```

## Resources (mean / max over the run, 130 samples)

VM CPU is the whole Docker VM from `/proc/stat` (4 vCPU = 100%), split into user and system (kernel) time. Process CPU is from `top` inside the VM in % of one core; it misses processes shorter than its 1 s interval, which includes most sandbox runs. Container CPU is `docker stats` (% of one core).

| Measure | Mean | Max |
|---|---|---|
| VM CPU busy | 63% | 92% |
| VM CPU user | 40% | 58% |
| VM CPU system (kernel) | 23% | 34% |
| VM CPU iowait | 3% | 4% |
| Sandbox containers alive | 1 | 3 |
| top: java | 26% | 89% |
| top: python | 1% | 29% |
| top: dockerd | 25% | 50% |
| top: containerd | 36% | 59% |
| top: runc | 0% | 6% |
| top: postgres | 2% | 9% |
| top: rabbitmq | 4% | 76% |
| docker stats CPU: api | 12% | 100% |
| docker stats CPU: worker | 14% | 35% |
| docker stats CPU: postgres | 4% | 18% |
| docker stats CPU: rabbitmq | 8% | 64% |
| memory: api | 514 MiB | 524 MiB |
| memory: worker | 849 MiB | 869 MiB |

## Correctness checks

| Check | Result |
|---|---|
| Submissions in the database for this run | 914 |
| Still PENDING / RUNNING after drain | 0 |
| INTERNAL_ERROR | 0 |
| Finalized exactly once (one `Submission N judged` api log line each) | 914 of 914 |
| Duplicate results ignored by the api (`already final`) | 59 |
| Submissions judged more than once by workers (extra judgings) | 59 (59) |
| Prometheus verdict counter deltas equal database counts | yes |

Verdicts (database):

| Language | Verdict | Database | Prometheus delta |
|---|---|---|---|
| java | accepted | 204 | 204 |
| java | time_limit_exceeded | 1 | 1 |
| java | wrong_answer | 227 | 227 |
| python | accepted | 368 | 368 |
| python | wrong_answer | 114 | 114 |

**Findings:**

- 1 verdict(s) differed from the expected verdict of the submitted solution

![Grafana during the run](saturation-3-workers.jpg)

## Analysis and findings (written after the run from the kept rows, logs and samples)

### Saturation point and bottleneck

- The queue drains up to **100 submissions/min offered** (97.5 judged/min, queue between 7 and 13). At 130/min it
  stops draining (15 to 77 during the step) and k6's p95 time to verdict jumps from 10.2 s to 66.8 s.
- From 100/min upwards the VM's CPU flattens at 76 to 82% busy (never 100%), about 30% of the VM is kernel time,
  and `dockerd` + `containerd` together use about 75% of one core. With all 3 workers busy, only **0.8 sandbox
  containers were running on average** (`sandboxes_alive`, 5 s samples). So most of each judging is spent creating,
  copying into, starting and removing containers (one compile container plus one container per test case), not
  running user code. The bottleneck is the container lifecycle through the Docker daemon, not raw CPU.
- Above saturation, judged throughput fell (97.5, then 81.0, then 68.5/min) instead of levelling off, because judge
  p50 rose from 1.9 s to 2.5 s while VM CPU stayed flat. The api's CPU rose from 7% to 23% (per-step means) as
  hundreds of waiting k6 VUs polled once a second, so polling was a suspect. The 130/min rerun
  (`saturation-130-rerun.md`: full 130/min offered, 3 s polling) judged **78.5/min**, close to the 81.0/min here, so
  1 s polling is **not** the main cause. What remains is that with a permanent backlog the three workers never idle
  and the judge time per job grows (p50 1.9 s at 100/min, 2.1 s at 130/min in the rerun); why sustained overload
  costs about 20% throughput against the 100/min step was not established.

Per-step means from the samples (VM CPU in % of 4 vCPU; others in % of one core):

| Offered/min | VM busy | VM system | api | worker | dockerd (top) | containerd (top) | Sandboxes running |
|---|---|---|---|---|---|---|---|
| 40 | 25% | 8% | 5% | 12% | 11% | 16% | 0.1 |
| 70 | 49% | 17% | 6% | 16% | 20% | 30% | 0.4 |
| 100 | 76% | 27% | 7% | 17% | 30% | 44% | 0.9 |
| 130 | 78% | 28% | 13% | 15% | 32% | 44% | 0.8 |
| 160 | 82% | 31% | 23% | 13% | 33% | 44% | 0.8 |

### Finding 1: a correct solution was judged TIME_LIMIT_EXCEEDED under contention

- Submission **1773**: the correct Java Two Sum solution (`two-sum-java-ok`), problem time limit 2,000 ms.
  Created 09:14:51.156 UTC (221.8 s after the scenario start, in the 70/min step), published 09:14:51.172,
  judged by worker-1 from 09:14:52.689 to 09:15:00.455 (7.8 s), finalized 09:15:00.504 as TIME_LIMIT_EXCEEDED on
  test 3 with 2,286 ms wall time and 42,652 KB memory. `attempts_count` 0, so it was not a requeue.
- The other 93 runs of the same solution in this test were ACCEPTED with median 251 ms and maximum 984 ms.
- The stall before it: from about 195 s to 211 s (09:14:24 to 09:14:40 UTC) VM CPU fell to 30 to 37% with **no
  sandbox container running** while submissions kept arriving at 70/min; then the backlog was judged in a burst
  (VM CPU 80 to 87% from 222 s to 241 s, `java` in top up to 89% of a core, queue 7 at 239 s) and submission 1773
  ran inside that burst. The cause of the stall was not identified (a Docker daemon or VM pause is possible but
  not shown).
- Cause of the wrong verdict: the time limit is enforced on container wall time (`State.StartedAt` to
  `FinishedAt`), so CPU contention on a shared host counts against the program. Not fixed during the measurements;
  the proposed fix is in `PROJECT.md` section 16.

### Finding 2: the sweeper re-publishes jobs that are only waiting in a long queue

- 59 submissions were re-published by the sweeper: 12, 43 and 4 at 720 s, 750 s and 780 s (Prometheus
  `codearena_sweeper_requeued_total`; api log "Requeued N stuck submission(s)" at 09:23:00, 09:23:31, 09:24:01 UTC),
  after the load had stopped (630 s) while the 174-job backlog drained. Their `last_queued_at` was over 2 minutes
  old because they were still queued, not lost.
- All 59 were judged twice by the workers and the api ignored all 59 second results (`already final`); verdict
  counters still equal the database. The extra work was 59 judgings during the drain, when the system was
  already behind.
- The first version of this file said 54 ignored results: the harness read the api log at about 09:24:46 while the
  last duplicate results were still being applied (the last at 09:24:50.688). The harness now also waits for
  `judge.jobs` and `judge.results` to be empty before analysing; this file was regenerated from the full logs.
- The sweeper cannot tell "queued" from "lost". Not changed during the measurements; proposed fix in section 16.

### Finding 3: k6 did not offer the full rate at 130 and 160/min

- k6 reported `dropped_iterations` = 122: the arrival-rate executor started with 60 pre-allocated VUs and reached
  only 182 (limit 500), so new iterations were dropped while VUs were being allocated. Created rates in the database
  were 116.0/min and 114.5/min instead of 130 and 160; the tables above use the measured created rate, not the
  target. A rerun of the 130/min step with more pre-allocated VUs and 3 s polling is in
  `saturation-130-rerun.md`.

### Status of this run

The correctness check reported one finding (finding 1). Everything else passed: 914 submissions, none left open,
no INTERNAL_ERROR, each finalized exactly once, Prometheus verdict counters equal to the database.
