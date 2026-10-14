"""Builds a problem's test set: the 2 samples (marked as samples) and the hidden cases, with expected outputs from
the reference solution.py. The 150+ small cross-check cases are generated too but are only used by validate.py.

    python3 tools/problems/build.py [slug ...] [--out DIR]

Without --out nothing is written; the counts are printed. Inputs are deterministic (seeded per slug and tier).
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pack import PROBLEMS_DIR, Pack, Runner, all_packs, build_inputs  # noqa: E402


def upload_cases(pack, runner, inputs=None):
    """The cases import.py uploads, in upload order: samples first, then hidden; outputs from solution.py."""
    inputs = inputs or build_inputs(pack)
    cases = []
    for text in inputs.samples:
        cases.append({"input": text, "expectedOutput": runner.python_file("solution.py", text)[0], "sample": True})
    for text in inputs.hidden:
        cases.append({"input": text, "expectedOutput": runner.python_file("solution.py", text)[0], "sample": False})
    return cases


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("slugs", nargs="*")
    ap.add_argument("--out")
    args = ap.parse_args()
    packs = [Pack(os.path.join(PROBLEMS_DIR, s)) for s in args.slugs] if args.slugs else all_packs()
    for pack in packs:
        runner = Runner(pack)
        inputs = build_inputs(pack)
        cases = upload_cases(pack, runner, inputs)
        size = sum(len(c["input"].encode()) + len(c["expectedOutput"].encode()) for c in cases)
        print(f"{pack.slug}: {len(inputs.samples)} samples, {len(inputs.hidden)} hidden "
              f"({len(inputs.large_indices)} max-size), {len(inputs.cross_check)} cross-check, {size:,} bytes uploaded")
        if args.out:
            target = os.path.join(args.out, pack.slug)
            os.makedirs(target, exist_ok=True)
            for i, case in enumerate(cases, 1):
                kind = "sample" if case["sample"] else "hidden"
                with open(os.path.join(target, f"{i:02d}-{kind}.in"), "w") as f:
                    f.write(case["input"])
                with open(os.path.join(target, f"{i:02d}-{kind}.out"), "w") as f:
                    f.write(case["expectedOutput"])


if __name__ == "__main__":
    main()
