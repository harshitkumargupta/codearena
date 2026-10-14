"""Validates problem packs; fails loudly (exit 1) when any check fails.

For each pack: metadata and statement sections; Python and Java references agree with brute.py on every small
cross-check case; the two references give identical output on the samples and every hidden case (and brute.py too
on all but the max-size ones); per-case and per-problem size limits; 8-15 hidden cases with 2-3 max-size; and the time
limit, which must be at least the slower reference's runtime on its slowest hidden case times 8 (minimum 2000 ms;
each language's slowest case is found from one timed pass and then re-run 3 times, median).

    python3 tools/problems/validate.py [slug ...] [--write-limits] [--report docs/PROBLEMS.md] [--cases N]
"""
import argparse
import concurrent.futures
import os
import re
import statistics
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pack import (MAX_CASE_BYTES, MAX_HIDDEN, MAX_LARGE, MAX_PROBLEM_BYTES, MAX_TIME_LIMIT_MS, MIN_HIDDEN,  # noqa: E402
                  MIN_LARGE, CROSS_CHECK_CASES, PROBLEMS_DIR, Pack, PackError, Runner, all_packs, build_inputs,
                  check_metadata, check_statement, normalize, required_time_limit, write_time_limit)

JOBS = max(2, (os.cpu_count() or 4) - 1)
TIMING_RUNS = 3


class Result:
    def __init__(self, pack):
        self.pack = pack
        self.errors = []
        self.stats = {}

    @property
    def ok(self):
        return not self.errors


def preview(text, limit=80):
    flat = text.replace("\n", "\\n")
    return flat if len(flat) <= limit else flat[:limit] + "..."


def run_all(runner, text, with_brute, timings=None):
    py_out, py_s = runner.python_file("solution.py", text)
    java_out, java_s = runner.java(text)
    outputs = {"python": normalize(py_out), "java": normalize(java_out)}
    if timings is not None:
        timings.append((py_s, java_s, text))
    if with_brute:
        outputs["brute"] = normalize(runner.python_file("brute.py", text)[0])
    return outputs


def agree(outputs):
    return len(set(outputs.values())) == 1


def validate(pack, cross_check_cases=CROSS_CHECK_CASES, measure=True, write_limits=False):
    result = Result(pack)
    result.errors += check_metadata(pack)
    result.errors += check_statement(pack.statement)
    if any(e.startswith("missing ") for e in result.errors):
        return result
    try:
        inputs = build_inputs(pack, cross_check_cases)
    except Exception as ex:  # a broken generator must fail validation, whatever it raised
        result.errors.append(f"gen.py failed: {ex!r}")
        return result
    if not MIN_HIDDEN <= len(inputs.hidden) <= MAX_HIDDEN:
        result.errors.append(f"{len(inputs.hidden)} hidden cases; must be {MIN_HIDDEN}-{MAX_HIDDEN}")
    if not MIN_LARGE <= len(inputs.large_indices) <= MAX_LARGE:
        result.errors.append(f"{len(inputs.large_indices)} max-size cases; must be {MIN_LARGE}-{MAX_LARGE}")
    for label, texts in (("sample", inputs.samples), ("hidden", inputs.hidden)):
        for i, text in enumerate(texts, 1):
            if len(text.encode()) > MAX_CASE_BYTES:
                result.errors.append(f"{label} {i}: input is {len(text.encode()):,} bytes (> {MAX_CASE_BYTES:,})")
    if result.errors:
        return result

    runner = Runner(pack)
    try:
        runner.compile_java()
        with concurrent.futures.ThreadPoolExecutor(JOBS) as pool:
            checks = list(pool.map(lambda t: run_all(runner, t, True), inputs.cross_check))
        bad = [(t, o) for t, o in zip(inputs.cross_check, checks) if not agree(o)]
        result.stats["cross_check"] = f"{len(checks) - len(bad)}/{len(checks)}"
        if bad:
            text, outs = bad[0]
            result.errors.append(f"cross-check: {len(bad)} of {len(checks)} small cases disagree; first input "
                                 f"{preview(text)!r}: " + ", ".join(f"{k}={preview(v, 40)!r}" for k, v in outs.items()))

        skip_brute = set(inputs.no_brute_indices)
        hidden_texts = inputs.samples + inputs.hidden
        flags = [True] * len(inputs.samples) + [i not in skip_brute for i in range(len(inputs.hidden))]
        timings = []
        with concurrent.futures.ThreadPoolExecutor(JOBS) as pool:
            outs = list(pool.map(lambda tf: run_all(runner, tf[0], tf[1], timings), zip(hidden_texts, flags)))
        mismatched = [i for i, o in enumerate(outs) if not agree(o)]
        result.stats["hidden_agree"] = f"{len(outs) - len(mismatched)}/{len(outs)}"
        if mismatched:
            i = mismatched[0]
            result.errors.append(f"samples+hidden: {len(mismatched)} case(s) disagree; first is case {i + 1}: "
                                 + ", ".join(f"{k}={preview(v, 40)!r}" for k, v in outs[i].items()))

        total = 0
        for text, out in zip(hidden_texts, outs):
            out_bytes = len(out["python"].encode())
            if out_bytes > MAX_CASE_BYTES:
                result.errors.append(f"an expected output is {out_bytes:,} bytes (> {MAX_CASE_BYTES:,})")
            total += len(text.encode()) + out_bytes
        result.stats["test_bytes"] = total
        if total > MAX_PROBLEM_BYTES:
            result.errors.append(f"test data is {total:,} bytes (> {MAX_PROBLEM_BYTES:,})")
        largest = max(inputs.hidden, key=lambda t: len(t.encode()))
        result.stats["largest_input"] = len(largest.encode())
        result.stats["tests"] = len(hidden_texts)
        result.stats["hidden"] = len(inputs.hidden)

        if measure:
            slowest_py = max(timings, key=lambda t: t[0])[2]
            slowest_java = max(timings, key=lambda t: t[1])[2]
            py = statistics.median(runner.python_file("solution.py", slowest_py)[1] for _ in range(TIMING_RUNS))
            jv = statistics.median(runner.java(slowest_java)[1] for _ in range(TIMING_RUNS))
            needed = required_time_limit(max(py, jv))
            result.stats.update(python_s=py, java_s=jv, needed_ms=needed)
            if needed > MAX_TIME_LIMIT_MS:
                result.errors.append(f"needs a {needed} ms time limit; the api allows at most {MAX_TIME_LIMIT_MS} ms "
                                     "(shrink the max-size input)")
            elif write_limits and needed > pack.meta["time_limit_ms"]:
                write_time_limit(pack.path("problem.yaml"), needed)
                pack.meta["time_limit_ms"] = needed
            elif pack.meta["time_limit_ms"] < needed:
                result.errors.append(f"time_limit_ms {pack.meta['time_limit_ms']} is below the measured requirement "
                                     f"{needed} (slower reference {max(py, jv):.3f} s x 8); run with --write-limits")
    except (PackError, OSError) as ex:
        result.errors.append(str(ex))
    except Exception as ex:  # a timeout or crash in a reference must fail validation, not abort the run
        result.errors.append(f"{type(ex).__name__}: {ex}")
    finally:
        runner.close()
    return result


def size_label(n):
    return f"{n:,} B" if n < 10 * 1024 else f"{n / 1024:,.0f} KiB"


def report_table(results):
    rows = ["| Slug | Title | Difficulty | Topic | Time limit | Tests (samples + hidden) | Largest input | "
            "Reference runtime Java / Python |", "|---|---|---|---|---|---|---|---|"]
    for r in results:
        m, s = r.pack.meta, r.stats
        rows.append(f"| `{r.pack.slug}` | {m['title']} | {m['difficulty'].title()} | {m['topic']} | "
                    f"{m['time_limit_ms']:,} ms | {s.get('tests', '?')} (2 + {s.get('hidden', '?')}) | "
                    f"{size_label(s.get('largest_input', 0))} | "
                    f"{s.get('java_s', 0) * 1000:,.0f} / {s.get('python_s', 0) * 1000:,.0f} ms |")
    return "\n".join(rows)


def replace_block(path, name, body):
    start, end = f"<!-- {name}:start -->", f"<!-- {name}:end -->"
    with open(path, encoding="utf-8") as f:
        text = f.read()
    if start not in text or end not in text:
        raise SystemExit(f"{path} has no {start} ... {end} block")
    text = re.sub(re.escape(start) + r".*?" + re.escape(end), lambda _: f"{start}\n{body}\n{end}", text, flags=re.S)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("slugs", nargs="*")
    ap.add_argument("--write-limits", action="store_true",
                    help="raise time_limit_ms to this measurement's requirement (never lowers it)")
    ap.add_argument("--report", help="markdown file with a <!-- validate:start/end --> block to fill")
    ap.add_argument("--cases", type=int, default=CROSS_CHECK_CASES)
    args = ap.parse_args()
    packs = [Pack(os.path.join(PROBLEMS_DIR, s)) for s in args.slugs] if args.slugs else all_packs()
    results = []
    for pack in packs:
        r = validate(pack, args.cases, write_limits=args.write_limits)
        results.append(r)
        s = r.stats
        timing = (f"java {s['java_s'] * 1000:.0f} ms, python {s['python_s'] * 1000:.0f} ms -> limit "
                  f"{pack.meta.get('time_limit_ms')} ms (needs {s['needed_ms']})") if "needed_ms" in s else ""
        print(f"{'PASS' if r.ok else 'FAIL'} {pack.slug:22} cross-check {s.get('cross_check', '-'):>9}  "
              f"samples+hidden {s.get('hidden_agree', '-'):>6}  {timing}", flush=True)
        for e in r.errors:
            print(f"     ERROR: {e}", flush=True)
    failed = [r.pack.slug for r in results if not r.ok]
    print(f"\n{len(results) - len(failed)} passed, {len(failed)} failed" + (f": {', '.join(failed)}" if failed else ""))
    if args.report and not failed:
        replace_block(args.report, "validate", report_table(results))
    raise SystemExit(1 if failed else 0)


if __name__ == "__main__":
    main()
