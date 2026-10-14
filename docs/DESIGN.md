# CodeArena design

Design document for CodeArena, an online judge with an AI tutor and live contests: goals, architecture,
specifications, the build phases, the decisions log and the open questions. The README has the quick start.

---

## 1. What we are building

A web platform where users solve programming problems in a browser editor. Submitted code runs inside locked-down Docker containers and returns a verdict. Admins host timed contests with a live leaderboard. An AI tutor gives graded hints and explains failed submissions without revealing the solution.

**Pitch line:** "A LeetCode/Codeforces-style judge with a sandboxed execution engine, queue-based workers, real-time contests, and an AI tutor that teaches instead of solving."

### Goals
1. Safe execution of untrusted code (CPU, memory, time, process, network limits).
2. Asynchronous judging through a queue, with horizontally scalable workers.
3. Real-time verdict updates and a live contest leaderboard.
4. AI hints that guide without giving away the answer.
5. Measurable performance numbers from a load test.

### Non-goals (do not build)
- Plagiarism checker (optional stretch only, after everything else is done).
- Languages beyond Java and Python (add C++ only as a stretch).
- Payments, social features, editorial content management, discussion forums.
- Kubernetes.

---

## 2. Tech stack

| Area | Choice | Notes |
|---|---|---|
| Language | Java 21 | Virtual threads optional for IO-heavy parts |
| Framework | Spring Boot 3.5.x | Verify latest stable before starting |
| Web | Spring Web (REST), Spring WebSocket (STOMP) | |
| Security | Spring Security, JWT, BCrypt | Roles: USER, ADMIN |
| Data | Spring Data JPA, PostgreSQL 16, Flyway | Migrations in Flyway |
| Cache / leaderboard | Redis 7 | Sorted sets for leaderboard |
| Queue | RabbitMQ 3.13 | Durable queue, manual ack, DLQ |
| Sandbox | Docker Engine via docker-java | Custom judge images |
| AI | Spring AI + Ollama (dev), hosted model (demo) | Provider set by config |
| Rate limiting | Bucket4j (Redis-backed) | |
| Observability | Spring Boot Actuator, Micrometer, Prometheus, Grafana | |
| Frontend | React + Vite + TypeScript, Monaco Editor, STOMP.js | Decision: React. Angular is acceptable if preferred |
| Testing | JUnit 5, Mockito, Testcontainers, k6 | |
| CI | GitHub Actions | |
| Packaging | Docker Compose | |

---

## 3. Architecture

```
React SPA
   |  REST + STOMP over WebSocket
   v
[ api ]  Spring Boot: auth, problems, submissions, contests, AI tutor, WebSocket
   |  publish job                     ^ publish result event
   v                                  |
RabbitMQ: judge.jobs  ---->  [ worker ]  (N instances)  ---->  RabbitMQ: judge.results
                               |  Docker API
                               v
                       sandbox containers (one per run)

api <-> PostgreSQL (source of truth)
api <-> Redis (leaderboard, rate limits, hint cooldowns)
api <-> Ollama / hosted LLM
```

### Services
- **api**: all HTTP and WebSocket traffic. Persists submissions as `PENDING`, publishes a job, consumes result events, updates DB and Redis, pushes updates to clients.
- **worker**: consumes jobs, runs code in sandboxes, publishes results. Stateless. Scale with `docker compose up --scale worker=3`.
- **common**: shared module with message DTOs (JudgeJob, JudgeResult), enums (Verdict, Language).

### Repo layout
```
codearena/
  docs/DESIGN.md
  docker-compose.yml
  common/            (Maven module: DTOs, enums)
  api/               (Spring Boot app)
  worker/            (Spring Boot app)
  judge-images/
    java/Dockerfile
    python/Dockerfile
  observability/     (prometheus.yml, Grafana provisioning and dashboard JSON)
  frontend/          (React + Vite)
  loadtest/          (k6 scripts)
  .github/workflows/ci.yml
  README.md
```

---

## 4. Judging flow

1. User submits `{problemId, language, sourceCode, contestId?}`.
2. api validates, rate-limits, saves Submission with status `PENDING`, publishes `JudgeJob` to `judge.jobs`, returns `submissionId`.
3. Client subscribes to `/topic/submissions/{submissionId}`.
4. worker consumes the job (manual ack). Status goes `RUNNING` (event published).
5. worker compiles if needed (Java: `javac`), inside a sandbox.
6. For each test case: run a fresh container with stdin, enforce limits, capture stdout, stderr, exit code, time, memory.
7. Compare output (trim trailing whitespace per line, ignore trailing blank lines). Stop at first failing test case.
8. worker publishes `JudgeResult` to `judge.results` and acks the job.
9. api consumes the result, updates the Submission row, updates the leaderboard if in a contest, and pushes the result over WebSocket.

### Verdicts
`PENDING, RUNNING, ACCEPTED, WRONG_ANSWER, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, RUNTIME_ERROR, COMPILATION_ERROR, INTERNAL_ERROR`

### Reliability rules
- Jobs are idempotent: worker checks the submission is not already finalized before running.
- Manual ack only after the result is published. Unacked jobs are redelivered if a worker dies.
- Poison jobs go to a dead-letter queue after 3 attempts, and the submission is marked `INTERNAL_ERROR`.
- A scheduled sweeper in api re-queues submissions stuck in `PENDING`/`RUNNING` for over 2 minutes.

---

## 5. Sandbox specification

Each run uses a fresh container that is removed afterwards. Applied via docker-java `HostConfig`:

- `network none`
- Memory limit (default 256 MB) with swap disabled (`memorySwap` equal to `memory`)
- CPU limit (`nanoCpus` for 1 CPU or less)
- `pidsLimit` (e.g. 64) to stop fork bombs
- Read-only root filesystem, plus a small writable `tmpfs` for the work directory
- Non-root user inside the image
- `no-new-privileges`, drop all capabilities
- Wall-clock timeout enforced by the worker (kill container on expiry) in addition to CPU-time measurement
- Output size cap (e.g. 1 MB) to stop output floods
- Source code and stdin passed in via a mounted temp directory or `copyArchiveToContainer`, never built into shell strings

### Judge images
- `judge-java`: `eclipse-temurin:21-jdk` based, non-root user, runs `javac Main.java` then `java -Xmx... Main`.
- `judge-python`: `python:3.12-slim` based, non-root user, runs `python Main.py`.

### Security tests that must exist (these become interview talking points)
- Infinite loop is killed and returns TLE.
- Memory hog returns MLE.
- Fork bomb is contained by the pids limit.
- Outbound network call fails.
- Writing to the filesystem outside tmpfs fails.
- Huge stdout is truncated and does not crash the worker.
- Reading another submission's files is impossible.

### Dev note
Worker needs access to the Docker daemon. In Compose, mount `/var/run/docker.sock` into the worker only. This is a known privilege risk, so document it in the README as a trade-off and name the production alternatives (gVisor, Firecracker, dedicated judge hosts).

---

## 6. Data model (PostgreSQL, via Flyway)

```
users(id, username UNIQUE, email UNIQUE, password_hash, role, created_at)

problems(id, slug UNIQUE, title, statement_md, difficulty, time_limit_ms,
         memory_limit_mb, created_by, published, created_at)

test_cases(id, problem_id FK, input, expected_output, is_sample BOOLEAN, ordinal)

submissions(id, user_id FK, problem_id FK, contest_id FK NULL, language,
            source_code, status, verdict_detail, failed_test_ordinal NULL,
            time_ms NULL, memory_kb NULL, attempts_count, created_at, judged_at)

contests(id, title, start_time, end_time, penalty_minutes DEFAULT 20, created_by)

contest_problems(contest_id FK, problem_id FK, label)   -- A, B, C...

contest_participants(contest_id FK, user_id FK, joined_at)

hint_logs(id, user_id FK, problem_id FK, submission_id FK NULL,
          level, created_at)
```

Indexes: `submissions(user_id, created_at DESC)`, `submissions(contest_id, user_id, problem_id)`, `submissions(status)` for the sweeper, `test_cases(problem_id, ordinal)`.

Hidden test case inputs and outputs are never returned by any user-facing endpoint. Only `is_sample = true` cases are visible.

---

## 7. API

All under `/api`. JWT in `Authorization: Bearer`.

**Auth**
- `POST /auth/register`, `POST /auth/login`, `GET /auth/me`

**Problems**
- `GET /problems` (paged, filter by difficulty), `GET /problems/{slug}` (statement + sample cases only)
- `POST /admin/problems`, `PUT /admin/problems/{id}`, `POST /admin/problems/{id}/testcases` (ADMIN)

**Submissions**
- `POST /submissions` (rate limited)
- `GET /submissions/{id}`, `GET /submissions/me?problemId=`

**Contests**
- `GET /contests`, `GET /contests/{id}`, `POST /contests/{id}/join`
- `GET /contests/{id}/leaderboard` (top N from Redis)
- `POST /admin/contests` (ADMIN)

**AI tutor**
- `POST /tutor/hint` body `{problemId, level, submissionId?}`
- `POST /tutor/explain-failure` body `{submissionId}`

### WebSocket (STOMP)
- Endpoint: `/ws` (JWT checked on CONNECT)
- `/topic/submissions/{submissionId}`: status changes (owner only)
- `/topic/contests/{contestId}/leaderboard`: leaderboard updates
- `/topic/contests/{contestId}/announcements`: admin messages (stretch)

---

## 8. Contest scoring and leaderboard

ICPC-style: rank by problems solved, then by total penalty (minutes).
- Penalty per solved problem = minutes from contest start to first accepted submission + `penalty_minutes` x wrong attempts before it.
- Only submissions during the contest window count. Wrong attempts on unsolved problems add no penalty.

Redis sorted set `contest:{id}:lb`, member = userId, score = `solved * 10_000_000 - totalPenaltyMinutes`. Recompute a user's score when their submission is judged, then `ZADD`, then broadcast the top N. Freeze option (stretch): stop broadcasting in the last 15 minutes.

---

## 9. AI tutor design

Goal: teach, not solve.

**Hint levels**
1. Nudge: which concept or pattern to think about.
2. Approach: outline the algorithm in plain words, no code.
3. Detailed: walk through the logic and complexity, still no full solution code.

**Failure explanation**: given the verdict and the user's code, explain the likely cause (off-by-one, overflow, missing edge case, TLE complexity issue).

**Inputs to the prompt:** problem statement, user's code, verdict, and for WRONG_ANSWER only the failing case if it is a sample case. Never include hidden test case data.

**Guardrails**
- System prompt forbids full solution code and says to answer in the requested level only.
- Post-check: if the response contains a code block longer than ~10 lines, reject and regenerate once with a stricter instruction, else return a fallback message.
- Cooldown per user per problem for level 3 hints, via Redis, and a daily cap per user.
- Log every hint in `hint_logs`.
- Use Spring AI `ChatClient`. Model and provider come from config, so Ollama locally and a hosted model for the demo need only property changes.
- Stream hint tokens to the client (SSE or WebSocket) as a stretch.

---

## 10. Security

- BCrypt password hashing, short-lived JWT access tokens, optional refresh token.
- Method-level role checks for admin endpoints.
- Bucket4j limits: submissions (e.g. 10/min/user), login attempts, AI calls.
- Request size limit on source code (e.g. 64 KB).
- Never log source code at INFO. Never expose hidden test data.
- CORS restricted to the frontend origin.
- Secrets via environment variables, `.env` not committed.
- Sandbox rules in section 5 are the main isolation layer.

---

## 11. Frontend pages

- Login / Register
- Problem list (filter, search)
- Problem page: statement on the left, Monaco editor on the right, language switch, Run on samples, Submit, live verdict panel, hint buttons (levels 1-3), submission history
- Contest list and contest page: timer, problem tabs, live leaderboard
- Admin: create problem, upload test cases, create contest
- Profile: solved count, recent submissions

UI quality matters for the demo, but keep it simple: clean layout, clear verdict colors, no heavy animation.

---

## 12. Build plan (phases)

### Phase 0: Setup (half a day)
- [x] Monorepo, Maven multi-module (`common`, `api`, `worker`)
- [x] `docker-compose.yml` with Postgres, Redis, RabbitMQ
- [x] Flyway baseline, health endpoints, `.gitignore`, `.env.example`

### Phase 1: Core backend (week 1)
- [x] Auth: register, login, JWT, roles
- [x] Problems and test cases CRUD (admin), public read (samples only)
- [x] Submission entity and `POST /submissions` saving `PENDING`
- [x] Seed 5-8 problems (Two Sum, FizzBuzz, Valid Parentheses, Reverse String, Fibonacci, etc.); Phase 7 added 24 more as problem packs (30 in total)

### Phase 2: Judge engine (week 1-2) (the core of the project) (done)
- [x] `judge-java` and `judge-python` images
- [x] Sandbox runner with all limits from section 5
- [x] Compile and run flow, output comparison, verdict mapping
- [x] Worker consuming `judge.jobs`, publishing `judge.results`, manual ack, DLQ
- [x] api consuming results and updating submissions
- [x] All security tests from section 5 passing

**Done when:** a submission posted via curl comes back `ACCEPTED` or the right failure verdict, and the abuse tests behave correctly.

### Phase 3: Real-time and contests (week 3) (done)
- [x] STOMP setup with JWT on CONNECT
- [x] Live submission status over WebSocket
- [x] Contests, join, scoring, Redis leaderboard
- [x] Live leaderboard broadcast
- [x] Stuck-submission sweeper

### Phase 4: AI tutor (week 4) (done)
- [x] Spring AI wired to Ollama
- [x] Hint levels and failure explanation endpoints
- [x] Code-block guardrail, cooldowns, hint logs
- [x] Config switch to a hosted provider verified (against a local fake OpenAI-compatible server; no real hosted provider tested yet)

### Phase 5: Frontend (runs in parallel from week 2, finishes week 4) (done)
- [x] Auth, problem list, problem page with Monaco, live verdicts
- [x] Contest page with leaderboard
- [x] Admin pages (minimal)

### Phase 6: Hardening and polish (week 5)
- [x] Bucket4j limits, input validation, error format (Phase 6a)
- [x] Testcontainers integration tests (Postgres, RabbitMQ, Redis) (Phase 6a: `integration-tests` module plus the per-module tests)
- [x] Micrometer metrics: queue depth, judge latency, verdict counts, with a Grafana dashboard (Phase 6b: Prometheus with DNS discovery of the worker replicas, provisioned Grafana dashboard, screenshot in `docs/screenshots/grafana-dashboard.png`; api, worker and frontend images plus the full Compose stack)
- [x] k6 load test, record the numbers (Phase 6c: `loadtest/`, six runs on 2026-10-14 with results, correctness checks and Grafana screenshots in `loadtest/results/`; three findings recorded in section 16)
- [x] GitHub Actions: build, test (Phase 6a: `.github/workflows/ci.yml` passes actionlint locally but has not run on GitHub yet)
- [x] README: architecture diagram, setup, trade-offs, screenshots (Phase 6c: `README.md`, plus `docs/DEMO.md`, `docs/DEPLOY.md`, `docs/INTERVIEW.md`); the demo GIF/video is not recorded yet
- [ ] Deployment or recorded demo: decided in Phase 6c (recorded demo is the default, a VM is optional, see section 16); the recording itself is not made yet

**Phase 6 status:** done except recording the demo video, which needs a person at the keyboard (script in `docs/DEMO.md`).

### Phase 7: Problem packs and tooling (done)
- [x] Pack format in `problems/<slug>/` (statement, Python and Java references, brute force, seeded generator, 2 samples) and tooling in `tools/problems/` (build, validate, import, verify_live, unit tests)
- [x] 24 original problems: 10 Easy, 10 Medium, 4 Hard, covering all 25 listed topics (see `docs/PROBLEMS.md`)
- [x] All 24 validated: references agree with each other and with the brute force on 160 small cases each, sizes and statements checked, time limits measured
- [x] Imported into the dev database (30 problems in the app) and verified live: both references ACCEPTED for all 24, 20 wrong solutions WRONG_ANSWER, 10 brute forces TIME_LIMIT_EXCEEDED
- [x] Problem list and problem pages checked in the browser (samples visible, hidden data absent); screenshot `docs/screenshots/07-problem-list.jpg`

### Stretch (only if everything above is done)
Status: none started.
- Plagiarism checker (winnowing / rolling-hash fingerprints)
- C++ language support
- Streaming AI hints
- Contest freeze and announcements

---

## 13. Testing and metrics

- **Unit:** output comparator, scoring and penalty calculation, verdict mapping.
- **Integration (Testcontainers):** submission to result end-to-end with real Postgres, RabbitMQ, Redis. Sandbox abuse tests.
- **Load (k6):** N concurrent users submitting. Record: submissions per minute sustained, p50/p95 judge latency, effect of scaling workers from 1 to 3, WebSocket fan-out for leaderboard updates.
- Put the measured numbers in the README and resume. Do not quote numbers that were not measured.

---

## 14. Interview material

The interview questions and the resume bullets with measured values are in [INTERVIEW.md](INTERVIEW.md).

---

## 15. Decisions log

| Decision | Choice | Reason |
|---|---|---|
| Frontend | React + Vite + TypeScript | Familiar, good Monaco support |
| Queue | RabbitMQ | Simpler than Kafka for job queues, ack and DLQ built in |
| Sandbox | Own docker-java runner, not Judge0 | The sandbox is the interview talking point |
| Services | api + worker + common | Shows horizontal scaling without microservice sprawl |
| Scoring | ICPC style | Well known, easy to explain |
| AI | Spring AI, Ollama in dev | Zero cost, provider switch by config |
| Spring Boot version | 3.5.16 (latest 3.5.x on Maven Central, Oct 2026) | Newest GA is 4.1.1, but 3.5 keeps Phase 0 aligned with this plan; revisit a 4.x upgrade (Jakarta EE 11, Spring Framework 7) once Spring AI/docker-java compatibility is confirmed |
| Postgres host port | 5433 by default (`POSTGRES_PORT`) | Dev machine already runs a native PostgreSQL on 5432; container still listens on 5432 internally |
| Primary keys | `BIGINT GENERATED BY DEFAULT AS IDENTITY` | Standard SQL identity, works with JPA `IDENTITY` and allows explicit ids in seed data |
| Enum columns | `VARCHAR` + `CHECK` constraints | Simpler than native Postgres enums with JPA `@Enumerated(STRING)`; adding a value is a one-line migration |
| Timestamps | `TIMESTAMPTZ` | Stored as UTC instants, needed for contest windows |
| test_cases index | `(problem_id, ordinal)` is UNIQUE | Same index the spec asks for, also prevents duplicate ordinals |
| JWT library | jjwt 0.13.0 (latest on Maven Central, Oct 2026), HMAC-SHA signed | Same builder/parser API as 0.12; no need for an OAuth2 resource server for a single issuer |
| JWT contents | `sub` = user id, plus `username` and `role` claims; no DB lookup per request | Stateless and fast; a role change applies when the token expires |
| JWT access-token lifetime | 2 hours by default (`JWT_ACCESS_TOKEN_TTL`), changed from 15 minutes | No refresh tokens yet, so 15 minutes would log users out mid-contest; a role change or a leaked token stays valid for up to 2 hours, so revisit if refresh tokens are added |
| JWT secret | `JWT_SECRET` required, no default, min 32 chars; app fails to start without it | Avoids shipping a guessable default secret |
| Refresh tokens | Not built in Phase 1 | Spec marks them optional; short access tokens are enough for the demo |
| Admin bootstrap | `ADMIN_USERNAME` / `ADMIN_EMAIL` / `ADMIN_PASSWORD` create one ADMIN if none exists; skipped with a WARN when unset | No hardcoded admin credentials in migrations |
| Login identifier | Username only | Simpler; email is stored lowercased and unique |
| Source size cap | 64 KiB (65 536 bytes) measured in UTF-8 bytes via a `@MaxUtf8Bytes` constraint | Character counts under-count multi-byte input; same constraint enforces BCrypt's 72-byte password limit |
| Submission read by non-owner | 403 Forbidden | Matches the spec wording "owner or admin only"; switch to 404 if hiding ids matters later |
| Submit to unknown or unpublished problem | 400 with a `problemId` field error | It is invalid input in the request body, not a missing URL resource |
| Admin test case upload | Batch (`{"testCases": [...]}`, max 100), ordinals auto-assigned; response returns only id, ordinal, sample flag | Hidden test data never appears in any response DTO, admin ones included |
| Pagination | `page` / `size` params, size clamped to 1..50, problems sorted by id, submissions by `createdAt` desc | Bounded queries without rejecting requests |
| Entity relations | Foreign keys mapped as plain `Long` ids, no JPA associations | No lazy-loading surprises with `open-in-view=false`; easy to explain |
| Difficulty | `EASY` / `MEDIUM` / `HARD` enum validated at the API, no DB CHECK | Spec lists CHECKs only for role, language and status |
| Seed problems | Flyway `V2__seed_problems.sql`: 6 problems (Two Sum, FizzBuzz, Valid Parentheses, Reverse String, Fibonacci, Maximum Subarray), stdin/stdout format, 2 samples + 3-5 hidden each | Expected outputs generated by reference solutions, not typed by hand |
| Hidden-data test | Web-slice test where the repository returns a hidden case and the response must not contain it; the service also filters `isSample` | Proves the response layer cannot leak even with a faulty query; a real-DB test comes with Testcontainers in Phase 6 |
| Docker client | docker-java 3.7.1 with the Apache HttpClient 5 transport | Latest release (Oct 2026); upstream marks httpclient5 as the long-term transport and future default |
| Docker host | `DOCKER_HOST`, default `unix:///var/run/docker.sock`; Colima uses `unix://$HOME/.colima/default/docker.sock` | Same variable the Docker CLI and Testcontainers use; see `judge-images/README.md` |
| Sandbox inputs | Copied with `copyArchiveToContainer` into a fresh anonymous volume at `/input` (root-owned, read-only to the sandbox user), not into tmpfs | Docker rejects archive copies into a read-only rootfs and cannot write into a tmpfs mount (verified); tmpfs `/tmp` (16 MB, noexec) stays the only writable path |
| Java compiled classes | Compile container writes to an anonymous `/out` volume; the worker copies the archive out, keeps the classes in memory (8 MB cap) and copies them into each run container's `/input` with that run's `stdin.txt` | Compile once, fresh container per run, nothing shared on disk between runs |
| Stdin and metrics | Fixed `judge-run` launcher in both images redirects `/input/stdin.txt`, then appends cgroup v2 `memory.peak` and `cpu.stat` usage to stderr; the worker strips that line | No shell string contains user input; peak memory and CPU time come from the container's own cgroup. Values are ignored when the run was killed |
| Wall time | Docker `State.StartedAt` to `FinishedAt` | Excludes API latency; the kill deadline is time limit + 1 s grace for container start, and a run whose measured wall time exceeds the limit is still a TIMEOUT |
| Java memory | `-Xmx` = problem limit, container limit = problem limit + 64 MB JVM overhead | Measured hello-world peak is about 40 MB; heap `OutOfMemoryError` counts as OOM, `unable to create native thread` (pids limit) does not |
| Output cap | 1 MiB per stream; exceeding it kills the container at once and returns OUTPUT_LIMIT with truncated output; Docker log driver `none` | Output floods end in about 100 ms instead of running to the time limit, and output never hits the daemon's disk |
| Output-cap kill | Issued from a separate virtual thread, never from the output callback thread | Killing from the thread that drains the attach stream deadlocked the daemon (found in testing) |
| Compile limits | 10 s, 512 MB container, javac heap 256 MB; any compile failure or timeout is COMPILE_ERROR | Bounded compile cost; applies to both languages since Python gained a compile step (see below) |
| SandboxResult extras | Adds `cpuTimeMs` and `outputTruncated` to the requested fields | Section 5 asks for CPU-time measurement alongside the wall-clock limit |
| Pids test | Linear spawner (Java and Python) asserts it stops below 64; separate Python exponential fork bomb asserts containment and that the next run still works | Java cannot fork itself, so it spawns `sleep` processes |
| Docker-gated tests | Abort in `@BeforeAll` with a printed reason when the daemon or judge images are missing | Build stays green without Docker; Surefire reports such a class as 0 tests run rather than skipped |
| Image builds | `judge-images/build.sh` with plain `docker build` | buildx is not installed on the dev machine, so the legacy builder prints a deprecation warning; the Dockerfiles avoid BuildKit-only syntax |
| Worker state | Worker is stateless with no database access; `JudgeJob` carries its test cases as `JudgeJob.TestCase(ordinal, input, expectedOutput)`. Idempotency (never re-finalizing a judged submission) is enforced by the api in Phase 2 step 3 | Workers scale without DB credentials. Jobs can get large (test data up to 1 MiB per case), well under RabbitMQ's 128 MB default message limit; revisit with a test-data cache if that becomes a problem |
| Test case type name | Nested `JudgeJob.TestCase` instead of a top-level `TestCase` | Avoids clashing with the api's `TestCase` entity |
| Python compile step | `python3 -X pycache_prefix=/tmp/pycache -m py_compile /input/Main.py` in a sandbox with the compile limits; the run still executes the source | Syntax errors now return COMPILATION_ERROR like Java; the pycache prefix keeps the write inside tmpfs since `/input` is read-only |
| OUTPUT_LIMIT verdict | WRONG_ANSWER with `failedTestOrdinal` and the fixed detail `Output limit exceeded` | Section 4 has no output-limit verdict; adding one needs a DB CHECK change in the api. Output over the 1 MiB cap cannot match any allowed expected output |
| Result detail policy | `detail` holds only truncated compiler output (`JUDGE_DETAIL_MAX_CHARS`, default 4000) or a fixed string (`Output limit exceeded`, `Internal judge error`); null for WA, TLE, MLE and RE. Run stdout, stderr and exit codes are never returned or logged | The program has read hidden input and could echo or encode it in any of those channels; compiler output only reflects the submitted source |
| INTERNAL_ERROR results | `failedTestOrdinal` is null | The failure is the judge's, not the submission's on that test |
| Result time and memory | Maximum over the tests that ran, including the failing one; time is container wall time (a TLE reports the kill time), memory is cgroup peak | Matches section 4; tests stop at the first failure, so later tests add nothing |
| Output comparison | Split on `\n`, `stripTrailing()` each line (also removes `\r` from CRLF), drop trailing blank lines; leading whitespace and leading or internal blank lines stay significant; empty expected output matches blank output | Section 4 step 7 rules, with CRLF handled without a separate pass |
| HTTP wire logging | `org.apache.hc.client5.http.wire` and `.headers` are OFF in `application.yml` and `logback-test.xml` | Wire logs would include the archive that carries hidden stdin to the sandbox |
| Delivery limit | `judge.jobs` is a quorum queue with `x-delivery-limit: 2` and dead-letters to `judge.jobs.dlq`; `JudgeQueues.MAX_ATTEMPTS = 3` | Verified on RabbitMQ 3.13.7: a limit of N allows N + 1 deliveries before dead-lettering (reason `delivery_limit`), so 3 attempts needs a limit of 2, not 3 |
| Queue topology | All three queues are durable quorum queues, declared by both api and worker from `JudgeQueues` in common with identical arguments; the default exchange routes by queue name | RabbitMQ rejects a redeclaration with different arguments (verified), so one shared definition makes start order irrelevant |
| Queue names | Fixed constants in `JudgeQueues`, no longer env-overridable | Replaces the Phase 0 `codearena.queues.*` properties; the names must match on both sides and are part of the protocol |
| `judge.results` delivery limit | Same limit of 2, no dead-letter target | A result the api fails to apply 3 times is dropped and logged; the submission stays open for the Phase 3 sweeper instead of blocking the queue |
| Message format | Jackson JSON via `Jackson2JsonMessageConverter` trusting only `com.codearena.common`; persistent delivery (Spring default); publisher confirms (`correlated`) and returns (`mandatory`) on both sides | Untrusted `__TypeId__` values are rejected; unroutable or nacked messages are treated as publish failures |
| Payload logging | `Message.setMaxBodyLength(0)` at startup in both apps; listeners take the raw `Message` and convert it themselves, logging only ids | Spring AMQP's own log lines print `Message.toString()`, which includes JSON bodies up to that length; with 0 they print only the byte count, at every log level |
| Record redaction | `toString()` of `JudgeJob`, `JudgeJob.TestCase` and also `JudgeResult` print lengths instead of source, test data and detail | `JudgeResult.detail` can hold compiler output, which quotes the submitted source |
| Publishing jobs | `@TransactionalEventListener(AFTER_COMMIT)` plus `@Async` builds the `JudgeJob` (all test cases, problem limits) in a new read-only transaction and waits up to 10 s for the confirm; RabbitMQ connection timeout is 5 s | The HTTP request never waits on or fails because of RabbitMQ; on failure the row stays PENDING and the error is logged for the Phase 3 sweeper |
| Worker ack policy | Publish RUNNING (best effort) -> judge -> publish final result and wait for its confirm -> ack. INTERNAL_ERROR or a failed result publish -> `basicNack(requeue=true)`; an unreadable or untrusted job -> `basicReject(requeue=false)`, straight to the DLQ | Every return counts toward the delivery limit, so judge-side failures and broker problems get 3 attempts and then reach the DLQ. A worker crash also counts (verified with kill -9). Permanent judge errors such as a job with no test cases are retried too, which keeps the policy simple |
| DLQ handling | api listener on `judge.jobs.dlq` marks a non-final submission INTERNAL_ERROR with detail `Judging failed after 3 attempts` | The DLQ message is consumed once handled; its arrival is logged with the `x-first-death-reason` |
| Result idempotency | Conditional JPQL updates: RUNNING only when PENDING, a final verdict only when PENDING or RUNNING; zero rows updated means the result is ignored | At-least-once delivery with an exactly-once effect; the worker does not check submission state (it has no database), so a redelivered job may be judged again and its result is ignored |
| api listener failures | Auto ack; a database exception requeues the result (bounded by the delivery limit); unreadable messages are logged by id and dropped | Transient database errors recover without losing results |
| Orphaned sandbox containers | `SandboxJanitor` runs at worker startup and every minute and removes sandbox containers older than 2 minutes | A worker killed mid-run leaves its container behind; 2 minutes is far beyond any sandbox lifetime, so containers in use by other workers on the same Docker host are never touched |
| Testcontainers (pulled forward) | One RabbitMQ Testcontainers test (`JudgePipelineRabbitTest`) proves the delivery limit and DLQ with the real topology and worker listener; Testcontainers 1.21.4 (Boot-managed) with docker-java modules aligned at 3.7.1 | The rest of the Testcontainers work stays in Phase 6. On Colima it needs `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` |
| Wrong attempts (ICPC) | WRONG_ANSWER, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED and RUNTIME_ERROR count; COMPILATION_ERROR, INTERNAL_ERROR, PENDING and RUNNING do not | Compile errors and judge failures are not attempts at the algorithm; open submissions count once they are judged, because scores are recomputed |
| Scoring details | Window is `[start_time, end_time)`; penalty minutes are whole minutes rounded down; attempts are ordered by `created_at`, then id | Ordering by submission time, not judging time, makes scores independent of the order in which verdicts arrive |
| Score encoding limits | `UserScore` rejects a penalty of 10,000,000 or more; contests last at most 30 days with at most 26 problems labelled A-Z | Keeps `solved * 10_000_000 - penalty` decodable; the worst case is far below the limit |
| Leaderboard ties | Equal scores share a rank (1, 1, 3); tied users are listed by username | Standard competition ranking; deterministic output |
| Leaderboard updates | After the result transaction commits, the user's score is recomputed from the database and written with ZADD; a join adds 0 with `ZADD NX`; a missing key is rebuilt under a staging key and RENAMEd into place | Redis is derived data: always correct after a recompute, never half-built for readers |
| Leaderboard when Redis fails | Reads fall back to computing the ranking from the database; a failed update is logged and swallowed | The database stays the source of truth. A failed update leaves that user stale until their next result or a rebuild (delete the key to force one) |
| Contest endpoint access | `GET /contests`, `GET /contests/{id}` and the leaderboard are public; join and submit need a login; join is allowed before and during the contest and is idempotent (`INSERT ... ON CONFLICT DO NOTHING`) | Matches the public problem pages; joining after the end returns 409 |
| Problems before the start | `GET /contests/{id}` returns `problemsVisible: false` and an empty list until the start | Contest problems must be published, so they are still visible in the public problem list; only which problems are in the contest is hidden. A "hidden until contest" problem state would be a separate change |
| Contest submission checks | In this order: contest exists (400 `contestId`), user joined (403), now within the window (400 `contestId`, "contest is not running"), problem in the contest (400 `problemId`); all time checks use the injected `Clock` | Distinct, testable failures; a non-participant never learns more than "not joined" |
| `attempts_count` | Number of times the sweeper re-published the job; the initial publish is not counted, and each publish can itself be delivered up to 3 times by RabbitMQ | When a stuck submission already has 3 sweeper requeues, the next sweep marks it INTERNAL_ERROR ("Judging did not finish after repeated requeues") instead of requeueing |
| Sweeper | Every 30 s (first run 30 s after startup), claims up to 50 PENDING/RUNNING rows whose `last_queued_at` (or `created_at` if never published) is over 2 minutes old with `FOR UPDATE SKIP LOCKED`, bumps `attempts_count` and `last_queued_at` in that short transaction, then publishes after commit. `last_queued_at` (Flyway V3) is also set on every confirmed publish | Concurrent api instances claim disjoint rows without waiting. If the original job is still queued, both copies are judged; the second result is ignored (verified end to end) |
| Testcontainers (pulled forward) | Redis (`LeaderboardRedisTest`) and PostgreSQL (`StuckSubmissionLockingTest`, real Flyway schema and the sweeper's real claim SQL over two JDBC connections) | The rest of the Testcontainers work stays in Phase 6 |
| Phase 3 checklist | Split "Contests, join, scoring, Redis leaderboard, live broadcast" into the leaderboard item (done) and "Live leaderboard broadcast" (Phase 3 step 2) | Broadcast needs the WebSocket work in step 2 |
| STOMP endpoint | Plain WebSocket at `/ws` (no SockJS), simple in-memory broker for `/topic`, allowed origins = the CORS origins; the HTTP handshake is `permitAll` because authentication happens in the CONNECT frame | Browsers must come from the frontend origin; clients without an `Origin` header (scripts, tests) are treated as same-origin by Spring |
| JWT on CONNECT only | The CONNECT frame's `Authorization: Bearer` token is validated once and becomes the session principal; SUBSCRIBE is authorized against that principal at subscribe time | A socket outlives its token: it keeps receiving for as long as it stays open after the 2-hour expiry, and a role change or removed access is not applied to open sockets. Clients reconnect after refreshing the token (docs/websocket.md); closing sessions at token expiry is a possible later addition |
| Subscription rules | Default deny: `/topic/submissions/{id}` for the owner or an ADMIN, `/topic/contests/{id}/leaderboard` for any authenticated user if the contest exists (same as the public REST endpoint); every client SEND is rejected | Server-to-client only. A rejected frame gets an ERROR frame and Spring closes the connection; ERROR messages come only from `StompRejectedException` (anything else becomes `Internal error`) |
| Pushed submission payload | `SubmissionStatusUpdate`: the GET /submissions/{id} fields without `sourceCode` and `verdictDetail`, plus `detailAvailable` | Deviates from "same DTO as GET": compiler output in `verdictDetail` quotes the submitted source, and no source may appear in a push. Clients GET the detail when `detailAvailable` is true |
| Status change events | `SubmissionResultService` is the only writer of status after creation and emits one `SubmissionStatusChangedEvent` per actual change (RUNNING, final verdicts, DLQ and sweeper INTERNAL_ERROR); after commit, `SubmissionStatusPublisher` pushes it and `LeaderboardUpdater` handles final statuses. Replaces `SubmissionFinalizedEvent` | One choke point, so no status path can skip the push. The initial PENDING is not pushed; clients GET after subscribing |
| Leaderboard broadcast | After a leaderboard ZADD (final contest verdict or join), a per-contest coalescer schedules one broadcast 1 s later (`LEADERBOARD_BROADCAST_INTERVAL`) and absorbs requests until it runs; the broadcast reads the current top 50 | At most about one broadcast per second per contest, always with the latest state. Measured end to end: 14 judged contest submissions in 4.2 s produced 4 broadcasts |
| Task scheduler | Explicit `taskScheduler` bean (pool of 2) for @Scheduled jobs and the coalescer | The STOMP broker defines its own scheduler, which would otherwise stop Boot from creating the default one |
| STOMP heartbeats | None configured | Simpler; clients rely on reconnect logic |
| In-memory broker scope | Pushes reach only sockets connected to the api instance that produced them | Fine for one instance. With several instances, results are consumed by whichever instance wins the queue, so sticky sessions alone do not help; see open questions |
| Testcontainers (pulled forward) | `WebSocketIntegrationTest` runs a real `WebSocketStompClient` against the running api with Testcontainers PostgreSQL; RabbitMQ listeners and the sweeper are switched off in that context | The rest of the Testcontainers work stays in Phase 6 |
| Manual STOMP check | `scripts/ws-check.mjs` (Node 22+, no dependencies) kept outside the app modules | Reproduces the end-to-end checks: CONNECT rejection, expired token, watching a topic |
| Spring AI version | Spring AI 1.1.8 via `spring-ai-bom`, with `spring-ai-starter-model-ollama` and `spring-ai-starter-model-openai` | Latest line built on Spring Boot 3.5 (its autoconfigure targets 3.5.15, compatible with our 3.5.16); Spring AI 2.0.x targets Spring Boot 4.1, so it is not used |
| Local model | `qwen2.5-coder:1.5b` on Ollama (0.99 GB download, about 1.1 GB resident, about 40 tokens/s on the GPU); Ollama installed with Homebrew and run as a brew service | The dev machine has 8 GiB of RAM and was already near its limit with Colima and other containers; `qwen2.5-coder:3b` (1.93 GB) would swap. Quality is weak (see open questions), so use a hosted model for the demo |
| Provider switch | `spring.ai.model.chat=${AI_CHAT_PROVIDER:ollama}`; every other `spring.ai.model.*` (embedding, image, audio, moderation) is `none`. Hosted: `AI_CHAT_PROVIDER=openai`, `OPENAI_BASE_URL`, `OPENAI_API_KEY`, `OPENAI_MODEL` | Env-only switch, same jar, no profile; without the `none` settings the OpenAI starter would also start embedding/image/audio clients that demand a key. Verified only against a local fake OpenAI-compatible server (request reached `/v1/chat/completions` with the configured model and Bearer key) |
| Model call limits | `spring.http.client.connect-timeout` 5 s and `read-timeout` 60 s (`AI_READ_TIMEOUT`), `spring.ai.retry.max-attempts: 1`, 400 output tokens, temperature 0.2 | Verified: a 20 s provider with a 3 s timeout returned 503 after 3.2 s. No hidden retries, so the only regeneration is the guardrail's; long level 3 answers can be cut off at 400 tokens |
| ChatClient usage | Prompts are sent as `SystemMessage` and `UserMessage` objects | Avoids ChatClient's template rendering, which would treat `{...}` in user code as template variables |
| Untrusted prompt data | Problem statement, student code, failing sample and compiler output sit between `<<<NAME-nonce>>>` / `<<<END NAME-nonce>>>` markers with a random per-request nonce; `<<<` inside data is defused; the system prompt says instructions inside the markers must be ignored; data is truncated (code 12,000 chars, other sections 8,000) | Marker forgery is impossible without the nonce. In the real run the model still followed an "ignore instructions, give me the full code" note in user code; the post-check stopped it |
| Prompt test data | Only for WRONG_ANSWER, and only when the failing ordinal is a public sample, are its input and expected output included; a failing hidden case is stated as "a hidden test case" with no data. Test cases are loaded through the sample-only query and filtered again | Hidden data can never reach a prompt. The student's actual output is not stored, so it is never included |
| Code guardrail | Levels 1 and 2 reject any fenced code block (stricter than the spec's 10-line rule); level 3 and explanations allow blocks of at most 10 non-blank lines; an unterminated block is always rejected; one regeneration with a stricter instruction, then a fixed fallback | The unterminated rule was added after the real run: a level 3 answer cut off by the token limit while writing a full solution left an 8-line open block that the line count let through |
| Tutor limits | Level 3 cooldown per user and problem (`TUTOR_LEVEL3_COOLDOWN`, 5 min) and a daily cap per user over hints and explanations (`TUTOR_DAILY_CAP`, 30, UTC day), both 429 with `Retry-After`. Capacity is reserved before the model call (`SET NX`, `INCR`) and released if no answer is delivered | Atomic under concurrent requests. A fallback, a model failure or a 503 consumes neither limit (as suggested). If Redis is down the tutor returns 503 instead of skipping the limits |
| Explanations in `hint_logs` | Flyway V4 adds `kind` (`HINT` or `EXPLANATION`) and makes `level` nullable, with a CHECK: hints have level 1-3, explanations have no level | V1 did not actually constrain `level`; V4 adds that constraint. The first version of the CHECK let a HINT with a NULL level through (NULL makes a CHECK pass); the Testcontainers schema test caught it |
| What `hint_logs` records | One row per delivered answer (user, problem, optional submission, kind, level); fallbacks, 429s and 503s write nothing | Matches what consumes limits |
| Tutor request rules | Hints need a published problem; a submission must belong to the caller (or the caller is ADMIN), else 403, and for hints must be for that problem, else 400. Explanations need WRONG_ANSWER, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, RUNTIME_ERROR or COMPILATION_ERROR, else 409 with the current status | INTERNAL_ERROR is the judge's failure, so it is not explained. Compiler output is included for COMPILATION_ERROR explanations |
| Tutor logging | Log lines carry only kind, level, user id, problem id, answer length, duration and the guardrail reason; Spring AI `log-prompt`/`log-completion` are off and `org.springframework.ai.retry` is at ERROR; model errors are logged by exception class only | Spring AI's retry WARN would include provider error bodies, which can echo prompts or output. Verified: no prompt, code, statement or model output in any log of the real run |
| Frontend stack | `frontend/`: React 19.3, Vite 8.3, React Router 8.4, TypeScript 6.0.3 (strict), `@monaco-editor/react` 4.7 with `monaco-editor` 0.57, `@stomp/stompjs` 7.4, `react-markdown` 10.1, Vitest 5 with Testing Library, ESLint 10 flat config with type-aware rules | TypeScript 7.0 is out, but typescript-eslint 8.71 supports only TypeScript below 6.1, so 6.0.3 is the newest version that lints |
| Styling | CSS Modules per component plus one `global.css` holding colour and spacing variables | No extra build step or utility vocabulary; verdict and difficulty colours live in one place |
| API base URL | `VITE_API_URL`, default `http://localhost:8080` (cross-origin). The dev server is pinned to port 5173 (`strictPort`) to match the api's default `CORS_ALLOWED_ORIGINS`. `VITE_API_URL=/` switches to the Vite dev proxy for `/api` and `/ws` (same origin) | Direct calls exercise the real CORS setup; proxy mode is the only way a browser can read `Retry-After` until the api exposes it (see open questions) |
| JWT storage | Session (token, expiry, user) in `localStorage` under `codearena.session` | Survives reloads and is shared across tabs with no backend change. Trade-off: any XSS can read the token. Mitigations: React escaping, `react-markdown` with `skipHtml` (raw HTML in statements and tutor answers is dropped), Monaco bundled locally (no CDN script), `no-console` lint error, STOMP debug output left off because CONNECT frames carry the token. An httpOnly cookie would remove the risk but needs cookie auth and CSRF protection in the api |
| Session expiry | Expiry = login time + `expiresInSeconds` - 30 s; a timer logs out at expiry and sends the user to /login with "Your session has expired"; any 401 on a request that carried a token clears the session and does the same; a 401 from a failed login does not | No refresh token exists, so expiry is handled by a clean re-login. Editor drafts are in memory only and are lost on that redirect |
| Route guard | Problem list is public; the problem page (editor, submit, verdicts, tutor, history) requires login and returns the user there after login | The api allows anonymous problem reads, but everything on the problem page except the statement needs a token |
| Live verdicts | One STOMP client per session token (JWT in the CONNECT frame); after every connect and reconnect it re-subscribes and then GETs `/submissions/{id}` (docs/websocket.md). stompjs reconnects every 3 s with the current token; if the socket is not connected within 5 s, or 3 attempts fail, the panel polls GET every 1.5 s and returns to live once connected. A final status always wins over RUNNING/PENDING, the subscription ends at the final status, and `verdictDetail` is fetched by GET when `detailAvailable` is true | Verified end to end: Java PENDING -> RUNNING -> ACCEPTED and Python PENDING -> RUNNING -> WRONG_ANSWER arrived over the socket (panel showed "Live") |
| Submission history | `GET /submissions/me?problemId=` (20 newest); rows still PENDING/RUNNING subscribe to their own topic; the list refetches after each submit and each final verdict | No user-level topic exists, so open rows subscribe individually |
| Monaco | Bundled from `monaco-editor` with Vite's `?worker` editor worker, in a lazy-loaded chunk (about 4 MB minified, 1 MB gzip; Vite warns about the size) | No CDN dependency; the entry chunk stays at about 424 KB and only the problem page pays for the editor |
| Tutor UI | Hint 1-3 and "Explain failure" (enabled only for WRONG_ANSWER, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, RUNTIME_ERROR, COMPILATION_ERROR); answers rendered as markdown without raw HTML; 429 shows the server message plus a countdown when `Retry-After` is readable and disables only that button; 503 and 4xx show the server message; no quota warnings | The api does not report remaining quota, so the UI shows no numbers it does not have |
| Frontend end-to-end check | Playwright in `frontend/e2e/` (`npm run e2e`, Chromium headless shell), separate from `npm test`; screenshots and a console report go to `frontend/e2e/output/` (git-ignored) | Keeps browser tests out of the unit test run; the run asserts that no console line contains the token or the submitted source |
| Contest clock | Countdowns and list badges use the client clock against the contest's start and end; the contest page trusts the status from `GET /contests/{id}` and, when the client clock says a boundary has passed but the server status disagrees, re-fetches every 3 s until it agrees | The api exposes no server time (see open questions). A fast client clock therefore cannot show problems early: they appear only once the server says RUNNING |
| Contest membership in the UI | "Joined" is known after pressing Join (idempotent) or when the user appears in the leaderboard; until then a running contest shows the problems with a "not joined yet" warning and the api's 403 decides | `ContestDetail` has no joined flag. The warning plus the inline 403 ("You have not joined this contest. Join the contest to submit.") make the rule visible without guessing |
| Contest page access | The contest page is public; problem tabs need a login; the leaderboard is live over STOMP when logged in and refreshes every 5 s when logged out | Same access as the REST endpoints; the socket needs a JWT |
| Contest workspace | The contest tabs reuse the problem workspace (statement, editor, verdict panel, history, tutor) with `contestId` on submit; drafts are kept in memory per contest, problem and language across tab switches; an ended contest shows the statement and history read-only with no editor | One workspace for practice and contests. The tutor stays available in contests; the Phase 4 open question about blocking it during contests still stands |
| Contest submit errors | Field errors on `contestId` and `problemId` are shown as sentences ("Contest is not running."); a 403 adds "Join the contest to submit." | The api's own messages, made readable |
| Live leaderboard | `useTopicSnapshot`: subscribe to `/topic/contests/{id}/leaderboard`, then GET once (again after every reconnect); a GET result is dropped if a push arrived while it was in flight; polls every 5 s while not live. The board's state lives in its own component with memoised rows, so a push re-renders only the board | Follows docs/websocket.md. Verified end to end: user B's board showed user A's solve without a reload |
| Admin pages | ADMIN-only nav link and route guard (cosmetic; the api enforces). Problem create/edit with a live markdown preview and a published toggle; editing loads published problems by slug, or the just-created problem from router state; the form is keyed by route so create and edit never share state | There is no admin read endpoint. The keying fixed a bug the e2e run found: after "Create problem" the edit page kept the create form's state |
| Test case upload | Batch upload (up to 100, each at most 1 MB) with file pickers that fill the input and expected-output text areas; the server assigns ordinals; the page lists only what was added in this session (ordinal, id, sample flag) | The api returns no hidden data and has no test-case list |
| Contest form | `datetime-local` inputs with seconds in the admin's local time zone (shown as a UTC offset), converted to ISO UTC; client checks mirror the api (end after start, at most 30 days, labels A-Z unique, problems unique and published) | The api still validates; its field errors are mapped back to the form fields |
| Profile page | Name, role, email, member-since, total submissions (`totalElements`) and the 20 most recent submissions with problem titles from the published list; no solved count | The api has no solved count, and paging through every submission to compute one was ruled out |
| README screenshots | Six curated JPEGs in `docs/screenshots/` (1440x900, quality 70, 22-106 KB each), written by `frontend/e2e/contests.spec.ts` | Small enough to commit; regenerated by rerunning the spec |
| e2e editor input | The Playwright helper pastes code through the clipboard | Typing into Monaco triggers auto-indentation on every line, which garbles code (and would break Python) |

| Rate limiting | Bucket4j 8.21.0 (`com.bucket4j:bucket4j_jdk17-core` and `bucket4j_jdk17-lettuce`; the old `bucket4j-core`/`bucket4j-redis` artifacts stop at 8.10.1) with CAS-based Lettuce buckets in Redis, shared by all api instances. Limits: submissions 10/min per user, login 30/min per IP and 10/min per username (lower-cased), register 10/h per IP, tutor 5/min per user; all in `codearena.rate-limit.*` with env overrides. Greedy refill; 429 carries `Retry-After` (seconds until the next token) and the standard body | Checked in controllers (and in `TutorService`) after validation and before any work. Buckets expire 10 s after they would be full again |
| Rate limiter connection | A dedicated Lettuce connection opened on first use, 2 s command timeout, `REJECT_COMMANDS` while disconnected | The api still starts without Redis, and an outage fails in milliseconds instead of queueing commands |
| Rate limiter outage | If Redis cannot be reached, every limited endpoint (submit, login, register, tutor) answers 503 with the standard body and logs a WARN with the exception class only; unlimited reads keep working | Fail closed, as the tutor already does. Accepting logins and submissions without limits during an outage would make the outage the attack window. Verified by stopping Redis: 503 within 10 ms, normal service after it came back |
| Client address | Per-IP limits use the connection's remote address. `X-Forwarded-For` is read only when the peer is in `RATE_LIMIT_TRUSTED_PROXIES`, walking the header right to left past trusted hops; the choice is logged at startup | Any client can forge the header. Verified: a spoofed header landed in the same bucket |
| Tutor rate limit | The per-minute tutor token is taken before the cooldown/daily-cap reservation and before any model call, so fallbacks, guardrail rejections and model errors all spend it. The cooldown and daily cap are unchanged | Closes the Phase 4 note that fallbacks cost nothing. Verified with a fake model that always returned a code fence: 5 fallbacks (10 model calls), then 429 with no model call |
| CORS `Retry-After` | `Access-Control-Expose-Headers: Retry-After` | The browser can read the countdown cross-origin; the Vite proxy is no longer needed for it (`frontend/.env.example` updated) |
| Tutor during contests | Hints and explain-failure answer 403 ("The AI tutor is not available for this problem while a contest that includes it is running.") for every non-admin user when the problem belongs to any contest whose window contains now, joined or not. Admins are exempt | Simple and fair: a participant cannot use a second account that never joined. Checked before the rate limit, so it costs no tokens. The frontend's tutor panel already shows the api's 4xx message, so no UI change |
| Request size | A servlet filter ahead of security: 256 KiB per request body, 32 MiB for `POST /api/admin/problems/{id}/testcases`; a declared `Content-Length` over the limit is rejected before reading, a chunked body is counted while read; both give 413 in the standard body | Tomcat's own limits do not cover JSON bodies. The admin limit leaves room for a batch of large cases |
| Test data cap | Per test case, input and output stay at most 1 MiB each; per problem, all test data together is capped at 64 MiB (`codearena.problems.max-test-data-bytes`), checked on upload with `octet_length` | Every judge job carries all of a problem's test cases and must stay under RabbitMQ's 128 MiB message limit |
| Validation audit | `LoginRequest` gained bounds (username at most 100 chars, password at most 72 UTF-8 bytes); every other request DTO was already bounded (titles 200, usernames 3-30, emails 255, statement 50,000 chars, source 64 KiB, test cases 1 MiB) | Unbounded login fields were the only gap |
| Error format | One body everywhere: `timestamp`, `status`, `error`, `message`, `path`, optional `fieldErrors`. Added a 406 handler, a 413 mapping for oversized chunked bodies, and an `ErrorController` replacing Boot's `/error` so container-level errors use the same body. `ErrorFormatMatrixTest` covers malformed JSON, 415, 406, 404, 405, 413, validation, type mismatch, 401 and 403 | Unknown routes without a token answer 401 (security runs first), which is the intended order |
| Integration tests module | `integration-tests` (failsafe, `*IT`) boots the api and the worker as two Spring contexts in one JVM, each from its own `application.yml` (`spring.config.location`), against Testcontainers Postgres, RabbitMQ and Redis and the real Docker sandbox. Settings are passed as command-line args so they beat the yml. The worker context excludes the JPA/Flyway/Redis/security auto-configuration and all Spring AI models that the api's jars bring onto the shared classpath | Tests: `JudgePipelineIT` (register, Java ACCEPTED, Python WRONG_ANSWER) and `ContestLeaderboardIT` (join, tutor 403, WA then AC, REST leaderboard and Redis ZSCORE equal `10_000_000 - penalty`). Skips with a reason when Docker or a judge image is missing |
| Existing Testcontainers tests | Kept in their modules (Redis leaderboard, Postgres locking, WebSocket, RabbitMQ DLQ, hint log schema, plus the new Redis bucket test); the new module adds only full-stack flows | Each existing test proves one component's behaviour in isolation, so moving them would only slow the per-module builds |
| Boot jars | `spring-boot-maven-plugin` now uses the `exec` classifier in api and worker, so the plain jar stays usable as a dependency; the runnable jars are `*-exec.jar` | Needed by the integration-tests module |
| docker-java alignment | `docker-java-api` is pinned to 3.7.1 in the parent | Testcontainers 1.21.4 pulled `docker-java-api` 3.4.2 next to `docker-java-core` 3.7.1 in the combined classpath (`NoClassDefFoundError: ImageHistoryCmd`) |
| CI | `.github/workflows/ci.yml`: `permissions: contents: read`, concurrency per ref, `ubuntu-24.04`. Backend: checkout@v7, setup-java@v6 (Temurin 21, Maven cache), a cgroup v2 check, `./judge-images/build.sh`, `mvn -B clean verify`, test reports uploaded on failure (upload-artifact@v7). Frontend: setup-node@v7 (Node 24, npm cache), `npm ci`, lint, typecheck, test, build. Playwright stays local | Action majors checked with `git ls-remote --tags`. actionlint 1.7.12 reports no issues. The workflow has not run on GitHub yet |
| CI must not skip | `CI_REQUIRE_DOCKER=true` (set in the backend job) turns every "Docker unavailable / image missing" skip into a failure | A green CI run with silently skipped sandbox tests would prove nothing. Verified locally: the full build passes with it set, 374 tests and 0 skipped |
| CI sandbox risks | Runner-dependent tests: memory reporting needs cgroup v2 `memory.peak` (kernel 5.19+), so the job runs on pinned `ubuntu-24.04` and fails fast if `/sys/fs/cgroup` is not cgroup2; the OOM tests depend on swap being disabled per container (`memory-swap = memory`), which cgroup v2 supports; the pids, network and read-only tests depend only on Docker. Timing: `JudgeServiceDockerTest` expects a Java ACCEPTED run under its 1.5 s limit and TLE above it, and `SandboxAbuseTest` allows 5 s of container overhead, so a slow shared runner could flake | Not changed yet. If CI flakes on timing, raise those limits rather than retrying |

| Container images | api and worker: multi-stage (`maven:3.9-eclipse-temurin-21` builds with a BuildKit cache mount for `~/.m2`, then `jarmode=tools extract --layers` from the `*-exec.jar`), runtime `eclipse-temurin:21-jre`, uid/gid 10002, `-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError`. Frontend: `node:24-alpine` build with `VITE_API_URL=/`, served by `nginxinc/nginx-unprivileged:1.30.5-alpine` (uid 101). Allow-list `.dockerignore` files, so `.env`, tests and `target/` never enter a build context | Dependency jars, loader and application classes are separate layers, so a code change rebuilds only the last one. No secret is passed at build time: `docker history` and a grep of `docker save` for every `.env` secret value found nothing |
| Container hardening | api, worker and frontend: `read_only: true` with a tmpfs `/tmp`, `cap_drop: ALL`, `no-new-privileges`, memory limits (api 1 GiB, worker 640 MiB) | The worker holds the Docker socket, so its own container is kept as small as possible. Postgres, Redis and RabbitMQ keep their images' defaults (their processes still run as uid 999) |
| Healthchecks | `curl` against `/actuator/health` on the management port. `eclipse-temurin:21-jre` (Ubuntu) already ships curl (checked), so nothing is installed; the probe runs as the image user 10002, not root. Frontend, Prometheus and Grafana use busybox `wget` | `depends_on: condition: service_healthy` orders the start-up: infrastructure, then api (Flyway migrates on start), then frontend. Workers wait only for RabbitMQ |
| Management port | `MANAGEMENT_PORT` (api 9080, worker 9081) serves `health`, `info`, `prometheus`. It is never published; the main port no longer serves actuator. Security permits `/actuator/prometheus` because the port is reachable only on the Compose network | Scrapes and healthchecks stay off the user-facing port. `/actuator/prometheus` through the frontend returns the SPA's `index.html` (nginx proxies only `/api` and `/ws`) |
| Published ports | Only the frontend (`FRONTEND_PORT`, default 8080). Prometheus (9090) and Grafana (3000) are bound to 127.0.0.1. Postgres, Redis and RabbitMQ are no longer published; `docker-compose.dev.yml` publishes them on 127.0.0.1 for the host-run workflow | Deviation from Phase 0, where the infrastructure ports were public. The dev override keeps `mvn spring-boot:run` working |
| Same-origin proxy | nginx proxies `/api/` and `/ws` (with `Upgrade`/`Connection` headers and a 1 h read timeout) to `api:8080`, re-resolving through Docker DNS (`resolver 127.0.0.11`) so a recreated api container is found. `CORS_ALLOWED_ORIGINS` defaults to `http://localhost:8080` | The browser sees one origin; STOMP needs no CORS |
| Trusted proxy | The Compose network has a fixed subnet (172.29.80.0/24, dynamic addresses limited to the lower /25) and the frontend a fixed address, 172.29.80.200, which is the only `RATE_LIMIT_TRUSTED_PROXIES` entry | Without it every request would come from nginx and share one per-IP bucket. A first attempt used .10, which a dynamically assigned container had already taken (found during verification) |
| Required secrets | `${VAR:?message}` for `POSTGRES_PASSWORD`, `RABBITMQ_PASSWORD`, `JWT_SECRET`, `ADMIN_USERNAME/EMAIL/PASSWORD`, `GRAFANA_ADMIN_PASSWORD`, `DOCKER_SOCKET_GID`; each service gets an explicit `environment` list, not `env_file` | Compose fails before creating anything and names the missing variable. The worker never sees the JWT secret or the admin password |
| Docker socket access | The worker mounts `/var/run/docker.sock`, joins `DOCKER_SOCKET_GID` with `group_add`, and sets `DOCKER_HOST=unix:///var/run/docker.sock` explicitly. On Colima the socket inside the VM is `root:docker` mode 660, gid 991 (`colima ssh -- stat -c %g /var/run/docker.sock`) | No hardcoded GID; the worker stays non-root. No host port on the worker, so `--scale worker=N` works |
| Judge images in Compose | `judge-java` and `judge-python` services behind the `judge-images` profile, built with `docker compose --profile judge-images build judge-java judge-python` | They build into the daemon the worker uses, and a plain `up` never starts them |
| Metric names and tags | `codearena.*` meters; tag keys limited to `verdict`, `language`, `outcome`, `limiter` plus `queue` (three fixed queue names, added for the queue-depth gauge); values are lowercase enum names. Every tag combination is registered at start-up, so series exist at zero | `MetricTags.ALLOWED` is enforced by guard tests that exercise every meter and then scan the whole registry (api and worker unit tests, plus `MetricsIT` against the real contexts). The submissions counter is `codearena.submissions` (`codearena_submissions_total`), because Prometheus strips a trailing `_created` (OpenMetrics reserves it) |
| Verdict counter in the api | Counted when the api's conditional `UPDATE ... WHERE status IN ('PENDING','RUNNING')` finalizes a submission, from a `SubmissionFinalizedEvent` handled after commit. End-to-end latency (`created_at` to `judged_at`) is recorded at the same point | The worker can judge one job more than once: a crash between publishing the result and acking redelivers the job, a judge-side error is nacked and retried, and the sweeper re-publishes a submission whose first job is still queued. A worker-side counter would count each of those. Verified: with the workers stopped, 6 submissions were requeued by the sweeper, all 12 jobs were judged, the api ignored 6 duplicate results, and the verdict counters matched the database exactly |
| Judge duration | Timer per delivery attempt in the worker (`codearena.judge.duration`, compile plus all runs), tagged language and verdict, with a percentile histogram between 10 ms and 5 min | Prometheus computes p50/p95 with `histogram_quantile` across all replicas; per-attempt is the right unit for worker capacity |
| Queue depth | The api polls `AmqpAdmin.getQueueInfo` for `judge.jobs`, `judge.results` and `judge.jobs.dlq` every `METRICS_QUEUE_DEPTH_INTERVAL` (15 s) into cached gauges; a failed poll sets NaN. The scheduler pool went from 2 to 3 threads | A scrape never calls the broker, and a stale value is never shown as current. Depth is ready messages only (unacked deliveries are not counted). The DLQ is drained by the api at once, so its depth is normally 0 |
| Tutor outcomes | `ok` (delivered), `fallback` (guardrail fallback), `blocked` (429 from the per-minute limit, cooldown or daily cap), `unavailable` (503: model or Redis). Contest 403s are not tutor calls | One counter increment per request at the service boundary |
| WebSocket sessions | Gauge over a set of STOMP session ids (added on `SessionConnectedEvent`, removed on `SessionDisconnectEvent`), per api instance | Spring can publish more than one disconnect event for a session; a set cannot go negative |
| Prometheus discovery | `dns_sd_configs` with `type: A`, `names: [worker]`, port 9081; the api is a static target. Scrape interval 5 s. `tasks.worker` is Swarm-only and not used | Plain Compose answers the service name with one A record per replica. Verified: after `--scale worker=3`, three worker targets plus the api, all up |
| Grafana | 13.2.4 with a provisioned Prometheus datasource (uid `prometheus`) and a file-provisioned dashboard (`observability/grafana/dashboards/codearena.json`, uid `codearena-overview`), anonymous access off. Prometheus v3.15.0 | Latest tags on Docker Hub at build time. Grafana 13.2 picked up an edited dashboard file only after a restart in this setup |
| AI provider without a key | When `AI_CHAT_PROVIDER=openai` and no key is set, an `EnvironmentPostProcessor` sets `spring.ai.model.chat=none` and logs a WARN; `ChatClientTutorModel` answers 503 whenever no `ChatModel` bean exists. In Compose, `OLLAMA_BASE_URL` defaults to `http://host.docker.internal:11434` (`extra_hosts: host-gateway`) | Found during verification: Spring AI's `OpenAiApi` throws "OpenAI API key must be set" at start-up, so the api crash-looped. Now verified: openai without a key and an unreachable Ollama both give 503 on the tutor while submissions keep working |
| Compose project name | `name: codearena` in `docker-compose.yml` | Previously the project took the folder name (`codearina`), so container, volume and image names depended on where the repo was cloned |
| `TutorLimiterRedisTest` clock | The fixed clock is 22:00 UTC of the real current day instead of 2026-10-13 | The test started failing on 2026-10-14 01:00 UTC with no code change: Redis expires keys on real time, and the daily key's `EXPIREAT` (fixed-clock midnight + 1 h) was already in the past. Production code was correct; only the test date was frozen |
| Load-test tool | k6 v2.3.0 (already installed with Homebrew), scripts in `loadtest/`, against the frontend container so requests go through nginx; `k6/websockets` for the STOMP clients | Same path as a browser. k6's built-in WebSocket module needs no extensions |
| Load-test rate limits | `docker-compose.loadtest.yml` raises only the api's five rate limits (to 100000); every result file states whether limits were raised, and `real-limits.md` runs with the real ones | k6 runs from one client IP with a few dozen users, so the real per-IP and per-user limits would make the test measure the limiter |
| Load-test data | Users and contests are created through the real api with names starting `lt<runid>_`; `cleanup.py` prints the rows matched by that prefix and deletes only them, and `run.sh` cleans up only after `analyze.py`'s correctness checks pass | The dev database is shared with the demo; nothing else may be touched. Credentials are generated per run, kept in the git-ignored `loadtest/.runs/`, and never printed |
| Load-test correctness | After every run: no submission left PENDING/RUNNING, no INTERNAL_ERROR, one `Submission N judged` api log line per submission, Prometheus verdict counter deltas equal to the database counts, and every verdict equal to the submitted solution's expected verdict. The drain waits for the run's rows to be final and for `judge.jobs` + `judge.results` to be empty | Throughput numbers are only worth quoting if the results are right. The queue wait was added after the first saturation analysis read the logs while sweeper duplicates were still being applied |
| Load-test measurements | Throughput from `created_at` / `judged_at` in Postgres; judge and end-to-end percentiles from the Prometheus histograms; time to verdict from k6 (submit to the poll that sees a final status, 250 ms polling, 1 s or 3 s in the open-model runs); VM CPU from `/proc/stat` read through a container, split into user and kernel time; per-process CPU from `top` in the Colima VM; sandboxes alive from `docker ps` | Several independent sources so each number can be cross-checked; `top` misses processes shorter than its interval, so the kernel-time split and the sandbox count carry the bottleneck evidence |
| Fan-out latency | Receive times logged by the k6 clients (no payload), matched to `judged_at` in the database and to the api's broadcast log lines, shifted by the measured host-VM clock offset; each burst of receipts is paired with its own broadcast | The board carries no timestamp. The first analysis matched receipts to the latest earlier log line, which produced a 2,853 ms artifact because the api logs after sending; corrected in the results file and in `analyze.py` |
| Findings during measurement | The false TLE, the sweeper duplicates, the nginx port exhaustion and the backlog throughput drop were recorded with evidence and proposed fixes (section 16) but not fixed | Fixing them mid-phase would change the system being measured |
| k6 summaries | `summaryOutputs` drops k6's `setup_data` before writing the JSON summary | `setup_data` contains the access tokens returned by `setup()`; the first load-test commit included them (tokens of deleted test users, signed with a throwaway secret) and was amended before anything was pushed |
| Demo seed | `scripts/demo-seed.py` creates three users with generated passwords printed once and a contest titled "CodeArena demo contest" on Two Sum, FizzBuzz and Valid Parentheses; existing users are kept unchanged and a running or upcoming contest with that title is reused | Safe to run twice (verified). The tutor part of the demo uses a problem outside the contest, because the tutor is blocked for problems in a running contest |
| Demo and deployment | Recorded demo by default (`docs/DEMO.md`); a single VM is optional (`docs/DEPLOY.md`) | The worker's docker.sock makes a public machine risky, and most free hosting does not offer a Docker host |
| Problem packs | `problems/<slug>/`: `problem.yaml`, `statement.md`, `solution.py`, `Main.java`, `brute.py`, `gen.py`, `samples/1.in`, `samples/2.in`, optional `wrong.py`. Only generators and samples are committed; `build.py` regenerates the 2 samples + 12 hidden cases (edge, small random, 2-3 max-size) from seeds `slug:tier:index` at import time | Reviewable diffs instead of megabytes of test data, and nothing to keep in sync: expected outputs always come from `solution.py`. Python's string seeding is stable across runs, so the tests are reproducible |
| Statement structure | Description, `### Input`, `### Output`, `### Constraints`, exactly like the seeded problems; no `### Samples` section in the markdown | The UI renders the sample test cases under the statement already; duplicating them in markdown could drift from the uploaded samples |
| Validation | `validate.py` requires agreement of the Python and Java references with `brute.py` on 160 small cases, of the two references on all samples and hidden cases (and `brute.py` on all but the heavy and max-size ones), 8-15 hidden with 2-3 max-size, 1 MiB per input/output, 4,000,000 bytes per problem, numeric constraints in the statement. `HEAVY_EDGE_CASES` exist for hand-written extremes the brute force cannot run | Wrong expected outputs are the worst thing a judge can ship; three independent implementations must agree before a test exists |
| Validation runtime | Python 3.12.15 from Homebrew (same version as the judge image) and Java 21 with the judge's run flags (`-Xss64m -XX:+UseSerialGC -XX:-UsePerfData`) | The host's system Python was 3.9; cross-checking on the judge's exact versions avoids version-specific surprises |
| Time limits | 8 x the slower reference's median runtime on its slowest hidden case, minimum 2000 ms, rounded up to 500 ms; `--write-limits` only raises a limit. All 24 ended at 2000 ms | Measurements taken right after the parallel cross-check pass (or with the stack running) were up to 2x the quiet runtime, so a reference near 250 ms flipped between 2000 and 3000 ms. Instead of chasing noise, the two slowest Python references were made faster with identical output (`pump-rate`: precomputed `v - 1` and `map(rate.__rfloordiv__, ...)`; `seating-plans`: bitmask backtracking), which put them well under the boundary |
| Import | `import.py` validates the pack in the same run, creates the problem unpublished, uploads samples then hidden cases, then publishes with `PUT`; a 409 on create means the slug exists and the pack is skipped | The api has no admin read or delete, so create-only is the only safe mode. Verified: re-importing an existing slug was skipped |
| Live verification | `verify_live.py` submits as the admin from `.env` (never printed), honours 429 `Retry-After` (admins have the normal 10 submissions/min limit), and requires ACCEPTED for both references, WRONG_ANSWER for every `wrong.py` and TIME_LIMIT_EXCEEDED for every `brute.py` flagged `SLOW_SHOULD_TLE` | The real judge in the real sandbox is the only proof the limits and tests behave. The first run found that the `alliance-queries` wrong solution timed out instead of answering wrong (its union without path compression degraded on the chain case); it was replaced with a fast but wrong one |
| Dev `.env` | `GRAFANA_ADMIN_PASSWORD` (generated) and `DOCKER_SOCKET_GID` were appended to the local `.env` in Phase 7; nothing else in it was changed | Compose requires them for every service since Phase 6b |

(Append new decisions here as they are made.)

## 16. Open questions

- ~~Deployment~~ Decided in Phase 6c: a **recorded demo is the default** (script in `docs/DEMO.md`); running on a single VM is optional and documented in `docs/DEPLOY.md` with a firewall checklist and the docker.sock warning. Nothing has been deployed. Still open: recording the demo video.
- ~~Which Ollama model to use for hints~~ Answered in Phase 4: `qwen2.5-coder:1.5b` (0.99 GB download, about 1.1 GB resident) is the largest coding model that fits the 8 GiB dev machine alongside Docker. Its hints are fast (2-25 s) but often weak or wrong: in the real run a level 2 hint for Two Sum suggested sorting (which loses the original indices) and called O(n log n) linear, and one failure explanation fell into a repetition loop. Use a hosted model for the demo; a 7B-class local model needs about 16 GB of RAM.
- Tutor guardrail limits (honest): the post-check only sees Markdown fenced code blocks. It cannot catch a full solution described in prose (shown in the real run: the level 2 FizzBuzz hint was the whole algorithm in words), indented or inline code, pseudocode written as a numbered list, a solution split into several short blocks at level 3, or advice that is simply wrong. The system prompt and levels reduce this but do not prevent it.
- ~~Tutor during contests~~ Closed in Phase 6a: the tutor answers 403 for non-admins on any problem that belongs to a running contest (see section 15).
- ~~Tutor cost control~~ Closed in Phase 6a: the per-minute tutor rate limit is spent before the model call, so fallbacks count.
- **Load-test finding: wall-clock time limit** (`loadtest/results/saturation-3-workers.md`, finding 1). A correct Java Two Sum (submission 1773) was judged TIME_LIMIT_EXCEEDED at 2,286 ms against a 2,000 ms limit right after a 20 s stall, while 93 other runs of it took at most 984 ms: container wall time includes CPU contention on a shared host. Proposed fix (not made, to keep the measured system unchanged): judge the limit on the program's CPU time (cgroup `cpu.stat` `usage_usec`, already read by `judge-run`) and keep a larger wall-clock cap (for example 3x the limit) only as a kill switch against sleeping or blocked programs; report TLE when CPU time exceeds the limit or the wall cap is hit.
- **Load-test finding: the sweeper cannot tell "queued" from "lost"** (`saturation-3-workers.md`, finding 2). During a 174-job backlog it re-published 59 submissions whose `last_queued_at` was over 2 minutes old only because they were still waiting; all 59 were judged twice (the api ignored the second results). Proposed fix (not made): skip or slow the sweep while `judge.jobs` has a backlog (the api already polls the depth), or compare the age against the expected queue wait (depth / measured throughput) instead of a fixed 2 minutes, or have the worker report RUNNING with a heartbeat and requeue only PENDING rows older than the backlog allows.
- **Load-test finding: nginx upstream connections** (`loadtest/results/real-limits.md`). At about 560 requests/s of quickly rejected submits nginx ran out of local ports (`connect() failed (99: Address not available)`) and answered 21,139 requests with 502, because it opens a new connection to the api per request. Proposed fix (not made): an `upstream api { server api:8080; keepalive 32; }` block with `proxy_http_version 1.1` and `proxy_set_header Connection ""` on `/api/` (this gives up the per-request DNS re-resolution, so restart the frontend when the api container is recreated, or use nginx's `resolve` parameter where available).
- **Load-test finding: throughput under a permanent backlog** (`saturation-130-rerun.md`). 3 workers judged 97.5/min at 100/min offered but about 80/min with a growing queue at 130/min, with or without 1 s polling; the cause of the drop was not established. The ceiling itself is set by container lifecycle cost (one container per test case, `dockerd` + `containerd` about 75% of a core, 0.8 sandboxes running on average with 3 workers busy). Options: run all test cases of a submission in one container, pre-create warm sandboxes, more judge hosts.
- **Problem packs: changing a published problem.** `import.py` is create-only because the api has no admin read or delete endpoints. To change a published problem's tests, either import the pack under a new slug (simplest; the old problem can be unpublished in the database), or do a manual database step: `UPDATE problems SET published = false WHERE slug = '<slug>';` then `DELETE FROM test_cases WHERE problem_id = (SELECT id FROM problems WHERE slug = '<slug>');`, upload the new tests with `POST /api/admin/problems/{id}/testcases` (the id is in the public `GET /api/problems/{slug}`), and publish again with `PUT /api/admin/problems/{id}`. Existing submissions keep their old verdicts. An import that fails after creating the problem leaves it unpublished and needs the same manual step.
- **Problem packs: api gaps.** No admin read, update-tests or delete endpoints (above); no topic or tag field on problems, so the topic lives only in `problem.yaml` and `docs/PROBLEMS.md` and the problem list cannot filter by it; test-case ordinals cannot be chosen (upload order decides them).
- Whether to include a "Run on custom input" feature (extra sandbox endpoint) or samples only.
- Frontend gaps the api does not cover yet (not built in the UI; no backend changes made in Phase 5):
  - "Run on samples" endpoint (custom-input runs) - expected.
  - Problem search - expected.
  - ~~`Retry-After` is not in `Access-Control-Expose-Headers`~~ Fixed in Phase 6a: the api exposes it.
  - No remaining tutor quota (daily cap or cooldown state) in any response, so the UI cannot warn before a call uses the last answer.
  - No refresh token: users log in again every 2 hours, and in-memory editor drafts are lost on that redirect.
  - `SubmissionSummary` has no `failedTestOrdinal`, so the history table shows the status only.
  - No user-level WebSocket topic (for example `/user/queue/submissions`), so each open submission in the history needs its own subscription.
  - No per-user solved status for problems, so the problem list cannot mark solved problems.
  - No server time: contest countdowns use the client clock, so a skewed clock shows the wrong time remaining (the page re-checks the server status before showing problems, but not the countdown itself). Fix: return `serverTime` in contest responses, or expose the `Date` header via CORS.
  - `ContestDetail` has no "joined" flag for the current user, so the UI only knows after Join or when the user is in the top N of the leaderboard.
  - No admin read endpoints: no admin problem list or GET by id (unpublished problems cannot be reopened for editing later), and no test-case list (ids, ordinals, sample flags) for a problem.
  - Test-case ordinals cannot be chosen; the server appends them.
  - `GET /submissions/me` cannot filter by contest, so a contest tab's history also shows practice submissions for that problem.
  - The leaderboard returns only the top N (50 by default), so a user below it cannot see their own rank.
  - No solved count for the profile page (and no solved marks in the problem list).
- WebSocket scale-out: the in-memory STOMP broker only reaches sockets on the same api instance. Before running more than one api instance, switch to a shared broker: RabbitMQ's STOMP plugin with `enableStompBrokerRelay` (RabbitMQ is already in the stack), or Redis pub/sub fanning each update out to every instance's local broker.
- ~~Compose services for `api` and `worker`, including the worker's `/var/run/docker.sock` mount and its documented trade-off~~ Done in Phase 6b: `docker compose up -d --scale worker=3` runs the whole system; only the worker mounts the socket (via `group_add` with `DOCKER_SOCKET_GID`), and the trade-off, mitigations and production alternatives are in `judge-images/README.md`.
- Real client IPs behind the frontend proxy: on Colima every host request reaches nginx from the Docker network gateway (172.29.80.1), so the per-IP login and register limits are shared by all browsers on the machine. On a Linux host, requests from other machines keep their source address with Docker's default iptables port publishing; a deployment behind another proxy or load balancer needs that proxy added to the trusted chain.
- `api` replicas: the api is a single instance in Compose. Scaling it needs the shared STOMP broker above and a load-balanced upstream in nginx.
