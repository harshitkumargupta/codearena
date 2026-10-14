# Interview material

Answers to the core interview questions about this project, from this code base. Class names point at the implementation;
numbers come from the load tests in `loadtest/results/` (2026-10-14, Mac17,5, Colima 4 vCPU / 6 GiB, other
workloads stopped).

## Core questions

### How do you stop a fork bomb, memory hog, or network access from user code?

Every compile and every test case runs in a fresh container created by `ContainerExecutor` (worker), with limits
set on docker-java's `HostConfig`: `--network none`, a memory limit with `memory-swap` equal to it so the program
cannot spill into swap, `pids-limit 64`, one CPU via `nano-cpus`, a read-only root filesystem with only a 16 MB
`noexec` tmpfs writable, all capabilities dropped, `no-new-privileges`, and a non-root user (10001). A fork bomb hits
the pids limit and the run fails as a runtime error; a memory hog is OOM-killed and the cgroup's `memory.peak` makes
it MEMORY_LIMIT_EXCEEDED; network calls fail because there is no interface. Wall-clock time is enforced by the
worker, which kills the container at the limit plus a 1 s grace, and output over 1 MiB kills the container at once.
`SandboxAbuseTest` proves each of these for Java and Python, including that a fork bomb leaves the worker healthy
and that one run cannot read another's files. In the running stack I submitted all five abuse programs used in the
demo (loop, 1 GiB allocation, fork bomb, HTTP call, write outside `/tmp`) and each was contained with no container
left behind.

### What happens if a worker crashes mid-judging? How do you avoid double-judging?

The worker uses manual acks with prefetch 1 (`JobProcessor`) and acks a job only after the broker has confirmed the
result message (`ResultPublisher` waits for the publisher confirm). If the worker dies before that, RabbitMQ
redelivers the job to another worker; after 3 deliveries (quorum queue `x-delivery-limit: 2`) it goes to
`judge.jobs.dlq` and `DeadLetterListener` marks the submission INTERNAL_ERROR. I don't prevent double-judging; I
make it harmless. The worker has no database, so a redelivered job is judged again, and the api applies results with
conditional updates in `SubmissionResultService` (final only from PENDING or RUNNING), so the second result updates
zero rows and is ignored. In the saturation test 59 submissions were judged twice and all 59 second results were
ignored; the verdict counts still matched the database exactly. The cost is wasted work, which is why the duplicate
judgings from the sweeper are a finding.

### Why a queue instead of judging inside the HTTP request?

A judging takes about 1 to 2 seconds at low load and much longer under load (judge p50 878 ms with one worker, 1,938
ms with three busy workers), and holding an HTTP request and a servlet thread for that long does not scale. The
queue decouples accepting work from doing it: `POST /submissions` returns in milliseconds (p50 7 to 27 ms in the load
tests) because it only saves a PENDING row and publishes after commit (`JudgeJobPublisher`). Workers scale
independently (`--scale worker=N`), and a burst is absorbed by the queue instead of timing out requests; the queue
depth gauge shows exactly when we fall behind. It also gives retries, dead-lettering and redelivery after a crash
for free. The trade-off is that the client needs a way to learn the result, which is the WebSocket push.

### How does the leaderboard stay correct under concurrent submissions?

Postgres is the source of truth and the Redis sorted set is derived data. After a result commits,
`LeaderboardUpdater` recomputes that user's whole score from their contest submissions in the database
(`ScoreCalculator`: problems solved, penalty minutes from the contest start plus 20 per earlier wrong attempt) and
writes it with `ZADD`. Because it recomputes rather than increments, a redelivered result, two results for the same
user arriving in any order, or a crash between steps all converge to the same value the next time that user is
recomputed. A missing key is rebuilt under a temporary key and renamed into place (`LeaderboardService.rebuild`), so
readers never see a half-built board, and if Redis is down, reads compute the ranking from the database. Broadcasts
are coalesced per contest to at most one per second and always read the latest state.

### How would you scale to 10x load? What would you change for production isolation?

The load test says the first limit is not the api or the database but the container lifecycle: at saturation (about
100 submissions/min with 3 workers on 4 vCPUs) VM CPU levels off below 100%, `dockerd` and `containerd` use about 75%
of a core, and only 0.8 sandboxes are running on average with 3 workers busy. So 10x means more judge hosts, not just
more worker processes on the same Docker daemon, and cheaper runs per submission: run all test cases in one
container instead of one container each, keep warm pre-created sandboxes, and judge on CPU time so contention can't
produce a false TLE (finding 1). The api would need a shared STOMP broker (RabbitMQ's STOMP relay) to run more than
one instance, nginx upstream keepalive (finding 3), and smarter clients that wait on the WebSocket instead of
polling. For isolation I'd stop handing the worker the host's Docker socket: run sandboxes under gVisor or in
Firecracker microVMs, on dedicated judge hosts that hold no data and only have queue credentials.

### How do you stop the AI tutor from leaking solutions or hidden test data?

Hidden data never reaches the model: `TutorContextLoader` loads only sample test cases, and for a wrong answer the
prompt includes the failing case only if it is a sample (`TutorPromptBuilder`); the judge itself never returns
program output for hidden tests. For solutions, the system prompt forbids solution code and states the level, and
`CodeBlockGuardrail` checks every answer: no fenced code block at hint levels 1 and 2, at most 10 lines at level 3
and in explanations. A violation triggers one retry with a stricter prompt, then a fixed fallback message; the
fallback does not consume the user's daily cap. Problems in a running contest are blocked for non-admins. I'm honest
about the gaps: a full solution written in prose, inline or unfenced code, or pseudocode as a numbered list gets
through, and the small local model sometimes gives wrong advice.

### Why Redis sorted sets for the leaderboard, and how did you encode the score?

A sorted set keeps members ordered by score with O(log n) updates and returns the top N with one `ZREVRANGE`, which
is exactly a leaderboard. ICPC ranks by problems solved (more is better) and then penalty minutes (fewer is better),
so I fold both into one number: `score = solved * 10,000,000 - penaltyMinutes`. Higher is better, any extra solved
problem outweighs any penalty, and both values decode back from the score. `UserScore` rejects a penalty of
10,000,000 or more so the encoding can't overflow into the solved part; contests are capped at 30 days and 26
problems, far below that. Ties share a rank (1, 1, 3) and are ordered by username.

## Five more questions the code invites

### You let the same job be judged twice. Why not prevent it?

Preventing it would need the worker to check and claim the submission in the database before judging, which gives
the stateless worker a database dependency, or a distributed lock that has its own failure modes (a worker that
holds the lock and dies). At-least-once delivery plus an idempotent consumer is simpler and is correct by
construction: the conditional update makes the second result a no-op, and verdicts are counted in the api only when
a submission is actually finalized, so metrics don't double either. The cost is extra work, and the load test showed
when it hurts: the sweeper re-published 59 jobs that were only waiting in a 174-job backlog. The fix I'd make is to
the sweeper (proposed in `docs/DESIGN.md` section 16), not to the idempotency model.

### Why quorum queues, and what was the delivery-limit surprise?

Quorum queues are RabbitMQ's replicated, durable queue type, and they track deliveries per message, so they can
dead-letter a poison message after a delivery limit without any code in the worker. Classic queues would need the
worker to count retries itself. The surprise: on RabbitMQ 3.13, `x-delivery-limit: N` allows N + 1 deliveries before
dead-lettering, so "3 attempts" needs a limit of 2 (verified in a test, recorded in the decisions log). Both services
declare all three queues from one definition in `JudgeQueues`, because RabbitMQ rejects a redeclaration with
different arguments.

### Why recompute a user's leaderboard score instead of incrementing it?

An increment is only correct if every event is applied exactly once and in order, and the pipeline guarantees
neither: results can be redelivered, judged twice, or arrive after a join. A recompute from the database is
idempotent: applying it once or five times gives the same score, and it fixes any earlier drift. It costs one query
over that user's contest submissions per result, which is small. The same reasoning makes Redis safe to lose: delete
the key and it is rebuilt from Postgres.

### How do you know the metrics are right?

The verdict counter is recorded from a `SubmissionFinalizedEvent` after the transaction commits, and only when the
conditional update changed a row, so a duplicate result cannot count twice (`SubmissionMetricsListener`). Tag keys
are restricted to an allowlist (`MetricTags`), enforced by tests that exercise every meter and then scan the whole
registry, so nobody can add a user id as a tag. Every load-test run ends with a correctness check in
`loadtest/analyze.py`: each submission finalized exactly once in the api log, none left open, and the Prometheus
verdict counter deltas equal to the database counts. All six runs passed that check, including the one with 59
duplicate judgings.

### What did the load test teach you that you didn't expect?

Three things. First, 3 workers gave only 1.36x the throughput of 1 (66.3 to 90.3 judged/min) because everything,
sandboxes included, shares 4 vCPUs and the Docker daemon. Second, a correct solution was judged
TIME_LIMIT_EXCEEDED (2,286 ms against a 2,000 ms limit, after a 20 s stall) because the limit is on wall time, so
contention counts against the user; judging on CPU time with a larger wall-clock cap would fix it. Third, the sweeper
can't tell a queued job from a lost one, and nginx ran out of local ports at about 560 requests/s because it opens a
new upstream connection per request. None of these were fixed during the measurements, so the numbers describe the
system as built; the fixes are in `docs/DESIGN.md` section 16.

## Resume bullets (measured values only)

- Built a sandboxed online judge in Spring Boot where untrusted Java/Python code runs in resource-limited Docker
  containers (CPU, memory, pids, no network, read-only filesystem), returning verdicts via a RabbitMQ worker pool.
- Designed an asynchronous judging pipeline with durable quorum queues, manual acks, dead-letter handling and a
  stuck-job sweeper, with exactly-once result handling; load-tested with k6 at 90 submissions/min over a 3-minute
  steady state with 3 workers (97.5/min at the saturation step) on a 4-vCPU laptop VM, with every run checked for
  exactly-once finalization.
- Implemented real-time contests with STOMP WebSockets and a Redis sorted-set leaderboard using ICPC scoring; 50
  concurrent WebSocket clients received 100% of 5,000 board updates, p95 58 ms after broadcast.
- Integrated Spring AI to provide graded hints and failure explanations with guardrails that block code-block
  solutions; local Ollama model by default, provider switchable by configuration.

### Claims that need a real run before they can be stated

- **Hosted model**: "switchable between local Ollama and hosted models" has only been verified against a local fake
  OpenAI-compatible server. Run the tutor against a real hosted provider before claiming it, and before claiming
  anything about hint quality (the local 1.5B model's quality is weak).
- **CI**: any claim like "CI pipeline with GitHub Actions" needs one green run on GitHub; the workflow has never run
  there.
- **Deployment**: "deployed" or "live demo" needs an actual deployment; today there is a recorded-demo plan only.
- **Throughput beyond this machine**: the numbers are from one laptop VM; don't extrapolate them to servers.
