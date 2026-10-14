# Demo script (4 to 5 minutes)

## Before recording

- Stack up from a clean state: `docker compose up -d --build --scale worker=3`, all services `healthy`.
- `python3.12 tools/problems/import.py --env-file .env` adds the 24 problem packs (30 problems in total).
- `python3 scripts/demo-seed.py --start-in 0` creates "CodeArena demo contest" (Two Sum, FizzBuzz, Valid
  Parentheses, running for 2 hours) and three users. It prints their passwords once; note two of them.
- Two browser windows side by side: **A** logged in as `demo_alice`, **B** as `demo_bob` (use a private window for
  B so the sessions do not share storage). Both on the contest page, both joined.
- A third tab with Grafana (`http://127.0.0.1:3000`, dashboard "CodeArena overview", last 15 minutes).
- Ollama running with `qwen2.5-coder:1.5b`, or a hosted model configured, for the tutor step. Warm it with one hint
  before recording; the first call loads the model.
- Have these snippets ready to paste: a correct and a wrong Two Sum, and the abuse program below.

## 1. What it is (0:00 to 0:30)

Show the problem list in window A: 30 problems, 6 seeded and 24 imported from problem packs.

> "CodeArena is an online judge like LeetCode or Codeforces. You write code in the browser, it runs in a locked-down
> Docker container on a pool of workers, and the verdict comes back live. It has timed contests with a live
> leaderboard, and an AI tutor that gives hints without giving away the answer."

## 2. Register and solve (0:30 to 1:15)

In a third, logged-out window, register a new user (5 seconds, then close it); stay in window A for the rest.
Open **Two Sum**, paste a correct Java solution, press Submit.

> "When I submit, the api saves the submission as PENDING, publishes a job to RabbitMQ and returns immediately.
> A worker picks it up, compiles in one container, runs every test case in its own fresh container, and publishes
> the result. The status you see change from pending to running to accepted is pushed over a WebSocket; the page
> never polls."

Point at the time and memory shown with the verdict.

## 3. Contest with two browsers and the live leaderboard (1:15 to 2:30)

Windows A and B on the contest page, leaderboard visible in both.

- In **B**, submit a wrong answer to problem A. Nothing changes on the board yet: a wrong attempt only costs
  penalty once the problem is solved.
- In **A**, submit the correct Two Sum. Alice moves to first in **both** windows within about a second.
- In **B**, submit the correct solution. Bob is placed behind Alice because of the 20-minute penalty for his
  wrong attempt.

> "Scoring is ICPC: problems solved, then penalty minutes, 20 per wrong attempt before the accepted one. The
> database is the source of truth. Redis keeps a sorted set per contest, recomputed from the database after every
> result rather than incremented, so a redelivered result can't corrupt it. Broadcasts are coalesced to at most one
> per second per contest; in the load test 50 clients received every board about 20 milliseconds after the api
> sent it."

## 4. Abuse being contained (2:30 to 3:15)

In window A, open **Fibonacci Number** (not part of the contest), choose Python, and submit:

```python
import os
while True:
    os.fork()
```

It comes back **Runtime Error** within a second, and the next normal submission still works.

> "That's a fork bomb. Each run gets a fresh container with no network, a memory limit with swap disabled, a limit
> of 64 processes, a read-only filesystem except a small tmpfs, a non-root user and no capabilities. The pids limit
> stops the fork bomb; the worker and the other runs are untouched."

Optional, if time allows: `while True: pass` comes back **Time Limit Exceeded**; allocating a gigabyte
(`bytearray(1024 * 1024 * 1024)`) comes back **Memory Limit Exceeded**.

Say the trade-off in one sentence:

> "The worker needs the Docker socket to start containers, which is root-equivalent on the host, so in production I'd
> run the sandboxes under gVisor or Firecracker or on dedicated judge machines."

## 5. Tutor hint (3:15 to 4:00)

Still on **Fibonacci Number** (the tutor is disabled for problems in a running contest, so a contestant can't use
it). Submit a wrong solution, then ask for a level 1 hint, then a level 2 hint.

> "Hints have three levels: a nudge, an approach, then a detailed walkthrough. A guardrail checks the model's answer:
> code blocks are not allowed at levels 1 and 2, and at most 10 lines at level 3. If the answer breaks the rule, it
> retries once with a stricter prompt and otherwise returns a fixed message. Hidden test data is never sent to the
> model. It has limits: a solution described in prose gets through, and the small local model's advice can be wrong."

If the model is slow or wrong on camera, say so; that's the honest state of the local model.

## 6. Grafana (4:00 to 4:45)

Switch to the Grafana tab.

> "Everything is instrumented with Micrometer and Prometheus. Here are the queue depths, submissions per minute,
> judge latency p50 and p95 from the workers' histograms, end-to-end latency, verdicts, the three live workers found
> by DNS discovery, WebSocket sessions, rate-limit rejections and tutor outcomes."

Point at the judge latency panel and the live-workers stat.

> "In the load test on this laptop, three workers sustained about 100 submissions a minute before the queue stopped
> draining. The bottleneck isn't CPU, it's starting and removing containers: with three workers busy, less than one
> sandbox was actually running on average."

## Closing line (4:45 to 5:00)

> "Spring Boot, RabbitMQ, Postgres, Redis, Docker, React. Verdicts are applied exactly once even when a job is
> judged twice, and the numbers in the README come from load tests I ran, findings included."
