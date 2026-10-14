"""Submits every pack's references through the real api and judge and checks the verdicts.

- solution.py (Python) and Main.java (Java) must both be ACCEPTED.
- wrong.py, where a pack has one, must be WRONG_ANSWER (at least 5 packs).
- brute.py, where gen.py sets SLOW_SHOULD_TLE = True, must be TIME_LIMIT_EXCEEDED (at least 5 packs); a slow
  solution that is accepted is reported, because it means the max-size tests do not stress the time limit.

Logs in as the admin from the env file (never printed). Honours 429 Retry-After, since submissions are rate limited.

    python3.12 tools/problems/verify_live.py [slug ...] [--base-url ...] [--env-file .env] [--report docs/PROBLEMS.md]
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pack import PROBLEMS_DIR, REPO, Pack, all_packs  # noqa: E402
from validate import replace_block  # noqa: E402

OPEN = ("PENDING", "RUNNING")
MIN_WRONG, MIN_SLOW = 5, 5


class Api:
    def __init__(self, base_url):
        self.base = base_url.rstrip("/") + "/api"
        self.token = None

    def call(self, method, path, body=None):
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = "Bearer " + self.token
        req = urllib.request.Request(self.base + path, method=method, headers=headers,
                                     data=None if body is None else json.dumps(body).encode())
        try:
            with urllib.request.urlopen(req, timeout=120) as res:
                raw = res.read()
                return res.status, json.loads(raw) if raw else None, res.headers
        except urllib.error.HTTPError as e:
            raw = e.read()
            try:
                return e.code, json.loads(raw), e.headers
            except ValueError:
                return e.code, None, e.headers

    def login(self, env_file):
        env = {}
        with open(env_file) as f:
            for line in f:
                if "=" in line and not line.startswith("#"):
                    k, v = line.strip().split("=", 1)
                    env[k] = v
        status, body, _ = self.call("POST", "/auth/login", {"username": env.get("ADMIN_USERNAME", ""),
                                                             "password": env.get("ADMIN_PASSWORD", "")})
        if status != 200:
            raise SystemExit(f"admin login failed (HTTP {status}); check the credentials in {env_file}")
        self.token = body["accessToken"]

    def problem_ids(self):
        ids, page = {}, 0
        while True:
            status, body, _ = self.call("GET", f"/problems?size=50&page={page}")
            for p in body["content"]:
                ids[p["slug"]] = p["id"]
            if page + 1 >= body["totalPages"]:
                return ids, body["totalElements"]
            page += 1

    def judge(self, problem_id, language, source):
        while True:
            status, body, headers = self.call("POST", "/submissions",
                                              {"problemId": problem_id, "language": language, "sourceCode": source})
            if status == 429:
                time.sleep(int(headers.get("Retry-After", "5")) + 1)
                continue
            if status != 201:
                return {"status": f"HTTP {status}", "timeMs": None}
            break
        sid = body["id"]
        deadline = time.time() + 600
        while time.time() < deadline:
            _, sub, _ = self.call("GET", f"/submissions/{sid}")
            if sub["status"] not in OPEN:
                return sub
            time.sleep(0.5)
        return {"status": "NO VERDICT", "timeMs": None}


def slow_flag(pack):
    with open(pack.path("gen.py")) as f:
        return "SLOW_SHOULD_TLE = True" in f.read()


def fmt(sub):
    t = sub.get("timeMs")
    return f"{sub['status']}" + (f" ({t} ms)" if t is not None else "")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("slugs", nargs="*")
    ap.add_argument("--base-url", default="http://localhost:8080")
    ap.add_argument("--env-file", default=os.path.join(REPO, ".env"))
    ap.add_argument("--report")
    args = ap.parse_args()
    packs = [Pack(os.path.join(PROBLEMS_DIR, s)) for s in args.slugs] if args.slugs else all_packs()
    api = Api(args.base_url)
    api.login(args.env_file)
    ids, total = api.problem_ids()
    print(f"published problems in the app: {total}", flush=True)

    failures, notes = [], []
    rows = ["| Slug | Java (Main.java) | Python (solution.py) | Wrong solution | Slow solution (brute.py) |",
            "|---|---|---|---|---|"]
    wrong_count = slow_count = 0
    for pack in packs:
        if pack.slug not in ids:
            failures.append(f"{pack.slug}: not published in the app")
            continue
        pid = ids[pack.slug]
        java = api.judge(pid, "JAVA", pack.read("Main.java"))
        python = api.judge(pid, "PYTHON", pack.read("solution.py"))
        for name, sub in (("Java", java), ("Python", python)):
            if sub["status"] != "ACCEPTED":
                failures.append(f"{pack.slug}: {name} reference got {sub['status']}")
        wrong_cell = slow_cell = "-"
        if pack.has("wrong.py"):
            wrong_count += 1
            sub = api.judge(pid, "PYTHON", pack.read("wrong.py"))
            wrong_cell = fmt(sub)
            if sub["status"] != "WRONG_ANSWER":
                failures.append(f"{pack.slug}: wrong.py got {sub['status']}, expected WRONG_ANSWER")
        if slow_flag(pack):
            slow_count += 1
            sub = api.judge(pid, "PYTHON", pack.read("brute.py"))
            slow_cell = fmt(sub)
            if sub["status"] == "ACCEPTED":
                notes.append(f"{pack.slug}: brute.py was ACCEPTED, so the max-size tests do not stress the limit")
            elif sub["status"] != "TIME_LIMIT_EXCEEDED":
                failures.append(f"{pack.slug}: brute.py got {sub['status']}, expected TIME_LIMIT_EXCEEDED")
        row = f"| `{pack.slug}` | {fmt(java)} | {fmt(python)} | {wrong_cell} | {slow_cell} |"
        rows.append(row)
        print(row, flush=True)
    if wrong_count < MIN_WRONG and not args.slugs:
        failures.append(f"only {wrong_count} packs have wrong.py; at least {MIN_WRONG} are required")
    if slow_count < MIN_SLOW and not args.slugs:
        failures.append(f"only {slow_count} packs set SLOW_SHOULD_TLE; at least {MIN_SLOW} are required")
    for n in notes:
        print("NOTE:", n)
    for f in failures:
        print("FAIL:", f)
    print(f"\n{len(packs)} packs checked, {wrong_count} wrong-solution checks, {slow_count} slow-solution checks, "
          f"{len(failures)} failures")
    if args.report and not failures:
        replace_block(args.report, "live", "\n".join(rows))
    raise SystemExit(1 if failures else 0)


if __name__ == "__main__":
    main()
