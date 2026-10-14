"""Imports problem packs through the admin api: create (unpublished), upload samples and hidden tests, then publish.

Create-only: the api has no admin read or delete endpoints, so an existing slug (409) is reported and skipped, never
duplicated or changed. A pack is imported only if validate.py passes for it in the same run. Admin credentials come
from the env file and are never printed.

    python3.12 tools/problems/import.py [slug ...] [--base-url http://localhost:8080] [--env-file .env]
"""
import argparse
import json
import os
import sys
import urllib.error
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from build import upload_cases  # noqa: E402
from pack import PROBLEMS_DIR, REPO, Pack, Runner, all_packs, build_inputs  # noqa: E402
from validate import validate  # noqa: E402

BATCH = 100


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
                return res.status, json.loads(raw) if raw else None
        except urllib.error.HTTPError as e:
            raw = e.read()
            try:
                return e.code, json.loads(raw)
            except ValueError:
                return e.code, None

    def login(self, env_file):
        env = {}
        with open(env_file) as f:
            for line in f:
                if "=" in line and not line.startswith("#"):
                    k, v = line.strip().split("=", 1)
                    env[k] = v
        status, body = self.call("POST", "/auth/login", {"username": env.get("ADMIN_USERNAME", ""),
                                                          "password": env.get("ADMIN_PASSWORD", "")})
        if status != 200:
            raise SystemExit(f"admin login failed (HTTP {status}); check ADMIN_USERNAME / ADMIN_PASSWORD in {env_file}")
        self.token = body["accessToken"]


def problem_body(pack, published):
    m = pack.meta
    return {"slug": pack.slug, "title": m["title"], "statementMd": pack.statement.strip(),
            "difficulty": m["difficulty"], "timeLimitMs": m["time_limit_ms"], "memoryLimitMb": m["memory_limit_mb"],
            "published": published}


def import_pack(api, pack):
    result = validate(pack)
    if not result.ok:
        return "skipped: validate failed (" + "; ".join(result.errors)[:200] + ")"
    status, body = api.call("POST", "/admin/problems", problem_body(pack, False))
    if status == 409:
        return "skipped: slug already exists (import is create-only)"
    if status != 201:
        return f"FAILED: create returned HTTP {status} {body and body.get('message')}"
    problem_id = body["id"]
    runner = Runner(pack)
    cases = upload_cases(pack, runner, build_inputs(pack))
    for i in range(0, len(cases), BATCH):
        status, body = api.call("POST", f"/admin/problems/{problem_id}/testcases", {"testCases": cases[i:i + BATCH]})
        if status != 201:
            return (f"FAILED: problem {problem_id} created unpublished, but uploading tests returned HTTP {status} "
                    f"{body and body.get('message')}; see docs/DESIGN.md section 16 for the manual fix")
    status, body = api.call("PUT", f"/admin/problems/{problem_id}", problem_body(pack, True))
    if status != 200:
        return f"FAILED: problem {problem_id} has its tests but publishing returned HTTP {status}"
    samples = sum(1 for c in cases if c["sample"])
    return f"imported as problem {problem_id}: {samples} samples + {len(cases) - samples} hidden, published"


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("slugs", nargs="*")
    ap.add_argument("--base-url", default="http://localhost:8080")
    ap.add_argument("--env-file", default=os.path.join(REPO, ".env"))
    args = ap.parse_args()
    packs = [Pack(os.path.join(PROBLEMS_DIR, s)) for s in args.slugs] if args.slugs else all_packs()
    api = Api(args.base_url)
    api.login(args.env_file)
    failures = 0
    for pack in packs:
        outcome = import_pack(api, pack)
        failures += outcome.startswith("FAILED") or outcome.startswith("skipped: validate")
        print(f"{pack.slug:22} {outcome}", flush=True)
    raise SystemExit(1 if failures else 0)


if __name__ == "__main__":
    main()
