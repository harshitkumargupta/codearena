# Problems

CodeArena ships with 6 seeded problems (Flyway `V2__seed_problems.sql`) and 24 problem packs in `problems/`, imported
through the admin api with the tools in `tools/problems/`. All statements are original; the algorithms are standard.

## Pack format

`problems/<slug>/`:

| File | Purpose |
|---|---|
| `problem.yaml` | title, slug, difficulty, topic, `time_limit_ms`, `memory_limit_mb` |
| `statement.md` | description, then `### Input`, `### Output`, `### Constraints` (the same structure as the seeded problems; samples are shown by the UI from the sample test cases) |
| `solution.py`, `Main.java` | efficient references in Python 3.12 and Java 21 (class `Main`, stdin/stdout); every expected output comes from `solution.py` |
| `brute.py` | slow but obviously correct solution for small inputs |
| `gen.py` | deterministic generator: `EDGE_CASES`, optional `HEAVY_EDGE_CASES` (too big for `brute.py`), `small(rng)`, `large(rng)`, optional `LARGE_COUNT`, optional `SLOW_SHOULD_TLE` |
| `samples/1.in`, `samples/2.in` | hand-written sample inputs |
| `wrong.py` (some packs) | a plausible wrong solution that must get WRONG_ANSWER in the live check |

Test sets are not committed: `tools/problems/build.py` generates them from fixed seeds (`slug:tier:index`) at import
time. Each problem gets the 2 samples plus 12 hidden cases: the hand-written edge cases, small random cases and 2-3
max-size cases. Another 160 small random cases are used only to cross-check the references against `brute.py`.

## Tools

```sh
python3.12 -m unittest discover -s tools/problems/tests           # tooling tests
python3.12 tools/problems/validate.py [slug ...] [--write-limits]  # all checks; exit 1 on any failure
python3.12 tools/problems/build.py [slug ...] [--out DIR]          # write the test set for inspection
python3.12 tools/problems/import.py [slug ...] --env-file .env     # create, upload tests, publish (create-only)
python3.12 tools/problems/verify_live.py [slug ...] --env-file .env --report docs/PROBLEMS.md
```

Python 3.12 is required because the judge runs Python 3.12 (`brew install python@3.12`). `validate.py` checks:

- metadata and statement sections, with numeric bounds under Constraints;
- the Python and Java references agree with `brute.py` on all 160 small cases;
- the two references give identical output on the samples and every hidden case, and `brute.py` agrees on all but the
  heavy and max-size cases;
- every input and expected output is at most 1 MiB and one problem's tests total at most 4,000,000 bytes, because the
  whole test set travels in one queue message;
- 8 to 15 hidden cases, 2 or 3 of them max-size;
- the time limit is at least 8 times the slower reference's runtime on its slowest hidden case (minimum 2000 ms,
  rounded up to 500 ms), measured on the host with the judge's Java flags and Python 3.12. This leaves room for the
  container start-up and contention seen in the load test.

## Problem list

Reference runtimes are medians of 3 runs on the slowest hidden case, on the development machine (Mac17,5, Apple A18
Pro, Python 3.12.15, OpenJDK 21.0.11), outside the sandbox. They include process start-up.

<!-- validate:start -->
| Slug | Title | Difficulty | Topic | Time limit | Tests (samples + hidden) | Largest input | Reference runtime Java / Python |
|---|---|---|---|---|---|---|---|
| `alliance-queries` | Alliance Queries | Hard | union-find | 2,000 ms | 14 (2 + 12) | 807 KiB | 52 / 65 ms |
| `almost-palindrome` | Almost a Palindrome | Easy | palindromes, two pointers | 2,000 ms | 14 (2 + 12) | 195 KiB | 26 / 22 ms |
| `archive-depths` | Archive Depths | Medium | binary search tree | 2,000 ms | 14 (2 + 12) | 27 KiB | 39 / 120 ms |
| `build-order` | Build Order | Hard | topological order | 2,000 ms | 14 (2 + 12) | 805 KiB | 106 / 125 ms |
| `busiest-floor` | Busiest Floor | Medium | binary tree | 2,000 ms | 14 (2 + 12) | 433 KiB | 85 / 57 ms |
| `busiest-window` | Busiest Window | Easy | sliding window | 2,000 ms | 14 (2 + 12) | 716 KiB | 50 / 56 ms |
| `cheapest-route` | Cheapest Route | Medium | shortest path (Dijkstra) | 2,000 ms | 14 (2 + 12) | 901 KiB | 71 / 99 ms |
| `courier-grid` | Courier Grid | Medium | graph BFS | 2,000 ms | 14 (2 + 12) | 245 KiB | 28 / 136 ms |
| `ferry-trips` | Ferry Trips | Easy | sorting and greedy | 2,000 ms | 14 (2 + 12) | 535 KiB | 85 / 64 ms |
| `festival-stalls` | Festival Stalls | Easy | 1D dynamic programming | 2,000 ms | 14 (2 + 12) | 478 KiB | 35 / 53 ms |
| `merge-bookings` | Merge Bookings | Medium | intervals | 2,000 ms | 14 (2 + 12) | 773 KiB | 100 / 98 ms |
| `next-warmer-day` | Next Warmer Day | Medium | monotonic stack | 2,000 ms | 14 (2 + 12) | 323 KiB | 57 / 64 ms |
| `orchard-harvest` | Orchard Harvest | Hard | 2D dynamic programming | 2,000 ms | 14 (2 + 12) | 352 KiB | 80 / 121 ms |
| `power-clusters` | Power Clusters | Medium | graph DFS and connected components | 2,000 ms | 14 (2 + 12) | 805 KiB | 45 / 92 ms |
| `power-mod-queries` | Power Remainders | Easy | math (modular exponentiation) | 2,000 ms | 14 (2 + 12) | 700 KiB | 63 / 100 ms |
| `pump-rate` | Pump Rate | Medium | binary search on the answer | 2,000 ms | 14 (2 + 12) | 773 KiB | 40 / 134 ms |
| `rainfall-totals` | Rainfall Totals | Easy | prefix sums | 2,000 ms | 14 (2 + 12) | 648 KiB | 51 / 49 ms |
| `recent-pings` | Recent Pings | Easy | queue | 2,000 ms | 14 (2 + 12) | 686 KiB | 27 / 58 ms |
| `reverse-the-train` | Reverse the Train | Easy | linked list | 2,000 ms | 14 (2 + 12) | 744 KiB | 55 / 44 ms |
| `rising-towers` | Rising Towers | Hard | longest increasing subsequence | 2,000 ms | 14 (2 + 12) | 673 KiB | 30 / 57 ms |
| `seating-plans` | Seating Plans | Easy | backtracking | 2,000 ms | 14 (2 + 12) | 32 B | 40 / 138 ms |
| `snack-backpack` | Snack Backpack | Medium | knapsack | 2,000 ms | 14 (2 + 12) | 1,194 B | 29 / 136 ms |
| `trending-tags` | Trending Tags | Medium | heap and top-k | 2,000 ms | 14 (2 + 12) | 482 KiB | 42 / 50 ms |
| `unpaired-badge` | Unpaired Badge | Easy | bit manipulation | 2,000 ms | 14 (2 + 12) | 773 KiB | 34 / 36 ms |
<!-- validate:end -->

## Live verification

Both references for every problem submitted through the real api and judge (Docker sandbox), plus the wrong-solution
and slow-solution checks. Times are the judge's reported maximum over the test cases.

<!-- live:start -->
| Slug | Java (Main.java) | Python (solution.py) | Wrong solution | Slow solution (brute.py) |
|---|---|---|---|---|
| `alliance-queries` | ACCEPTED (181 ms) | ACCEPTED (142 ms) | WRONG_ANSWER (187 ms) | TIME_LIMIT_EXCEEDED (3080 ms) |
| `almost-palindrome` | ACCEPTED (255 ms) | ACCEPTED (181 ms) | WRONG_ANSWER (110 ms) | - |
| `archive-depths` | ACCEPTED (185 ms) | ACCEPTED (215 ms) | - | - |
| `build-order` | ACCEPTED (235 ms) | ACCEPTED (264 ms) | WRONG_ANSWER (85 ms) | TIME_LIMIT_EXCEEDED (3124 ms) |
| `busiest-floor` | ACCEPTED (268 ms) | ACCEPTED (123 ms) | WRONG_ANSWER (69 ms) | - |
| `busiest-window` | ACCEPTED (180 ms) | ACCEPTED (135 ms) | WRONG_ANSWER (58 ms) | TIME_LIMIT_EXCEEDED (3071 ms) |
| `cheapest-route` | ACCEPTED (122 ms) | ACCEPTED (146 ms) | WRONG_ANSWER (57 ms) | TIME_LIMIT_EXCEEDED (3089 ms) |
| `courier-grid` | ACCEPTED (169 ms) | ACCEPTED (324 ms) | WRONG_ANSWER (60 ms) | - |
| `ferry-trips` | ACCEPTED (442 ms) | ACCEPTED (130 ms) | WRONG_ANSWER (60 ms) | - |
| `festival-stalls` | ACCEPTED (77 ms) | ACCEPTED (82 ms) | WRONG_ANSWER (43 ms) | - |
| `merge-bookings` | ACCEPTED (162 ms) | ACCEPTED (90 ms) | WRONG_ANSWER (41 ms) | - |
| `next-warmer-day` | ACCEPTED (319 ms) | ACCEPTED (161 ms) | WRONG_ANSWER (58 ms) | TIME_LIMIT_EXCEEDED (3074 ms) |
| `orchard-harvest` | ACCEPTED (298 ms) | ACCEPTED (146 ms) | WRONG_ANSWER (58 ms) | TIME_LIMIT_EXCEEDED (3062 ms) |
| `power-clusters` | ACCEPTED (405 ms) | ACCEPTED (890 ms) | - | - |
| `power-mod-queries` | ACCEPTED (251 ms) | ACCEPTED (200 ms) | WRONG_ANSWER (56 ms) | TIME_LIMIT_EXCEEDED (3115 ms) |
| `pump-rate` | ACCEPTED (200 ms) | ACCEPTED (156 ms) | WRONG_ANSWER (60 ms) | TIME_LIMIT_EXCEEDED (3108 ms) |
| `rainfall-totals` | ACCEPTED (500 ms) | ACCEPTED (110 ms) | WRONG_ANSWER (54 ms) | - |
| `recent-pings` | ACCEPTED (220 ms) | ACCEPTED (146 ms) | WRONG_ANSWER (60 ms) | TIME_LIMIT_EXCEEDED (3098 ms) |
| `reverse-the-train` | ACCEPTED (250 ms) | ACCEPTED (304 ms) | - | - |
| `rising-towers` | ACCEPTED (260 ms) | ACCEPTED (110 ms) | WRONG_ANSWER (112 ms) | TIME_LIMIT_EXCEEDED (3070 ms) |
| `seating-plans` | ACCEPTED (211 ms) | ACCEPTED (173 ms) | - | - |
| `snack-backpack` | ACCEPTED (113 ms) | ACCEPTED (167 ms) | WRONG_ANSWER (60 ms) | - |
| `trending-tags` | ACCEPTED (382 ms) | ACCEPTED (91 ms) | WRONG_ANSWER (84 ms) | - |
| `unpaired-badge` | ACCEPTED (130 ms) | ACCEPTED (92 ms) | WRONG_ANSWER (42 ms) | - |
<!-- live:end -->
