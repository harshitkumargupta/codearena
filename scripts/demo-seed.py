#!/usr/bin/env python3
"""Seeds a demo: one admin-created contest on seeded problems and a few demo users.

Safe to run twice: existing demo users are kept (their passwords are not changed and are not shown again), and a
running or upcoming contest with the demo title is reused. New users get generated passwords that are printed once,
here, and stored nowhere. Uses the api through the frontend proxy; admin credentials come from the .env file.

    python3 scripts/demo-seed.py [--base-url http://localhost:8080] [--env-file .env] [--start-in 2] [--minutes 120]
"""
import argparse
import datetime
import json
import os
import secrets
import sys
import urllib.error
import urllib.request

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTEST_TITLE = "CodeArena demo contest"
CONTEST_PROBLEMS = [("two-sum", "A"), ("fizzbuzz", "B"), ("valid-parentheses", "C")]
DEMO_USERS = ["demo_alice", "demo_bob", "demo_carol"]


def call(method, url, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, method=method, headers=headers,
                                 data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req, timeout=30) as res:
            raw = res.read()
            return res.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, None
    except urllib.error.URLError as e:
        sys.exit(f"cannot reach {url}: {e.reason} (is the stack up?)")


def read_env(path):
    env = {}
    with open(path) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                env[k] = v
    return env


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base-url", default="http://localhost:8080")
    ap.add_argument("--env-file", default=os.path.join(REPO, ".env"))
    ap.add_argument("--start-in", type=int, default=2, help="minutes until the contest starts (0 = now)")
    ap.add_argument("--minutes", type=int, default=120, help="contest length in minutes")
    args = ap.parse_args()
    api = args.base_url.rstrip("/") + "/api"

    env = read_env(args.env_file)
    status, body = call("POST", api + "/auth/login",
                        {"username": env.get("ADMIN_USERNAME", ""), "password": env.get("ADMIN_PASSWORD", "")})
    if status != 200:
        sys.exit(f"admin login failed (HTTP {status}); check ADMIN_USERNAME / ADMIN_PASSWORD in {args.env_file}")
    admin = body["accessToken"]

    print("Demo users:")
    for name in DEMO_USERS:
        password = secrets.token_urlsafe(12)
        status, body = call("POST", api + "/auth/register",
                            {"username": name, "email": f"{name}@demo.codearena.local", "password": password})
        if status == 201:
            print(f"  {name:12} password: {password}   (shown once; not stored anywhere)")
        elif status == 409:
            print(f"  {name:12} already exists; password unchanged (it was printed only when first created)")
        else:
            sys.exit(f"registering {name} failed: HTTP {status} {body and body.get('message')}")

    status, body = call("GET", api + "/contests?size=50")
    existing = [c for c in body["content"] if c["title"] == CONTEST_TITLE and c["status"] in ("UPCOMING", "RUNNING")]
    if existing:
        contest = existing[0]
        print(f"\nReusing contest {contest['id']} ({contest['status'].lower()}, "
              f"{contest['startTime']} to {contest['endTime']})")
    else:
        status, body = call("GET", api + "/problems?size=50")
        problems = {p["slug"]: p["id"] for p in body["content"]}
        missing = [slug for slug, _ in CONTEST_PROBLEMS if slug not in problems]
        if missing:
            sys.exit(f"seeded problems missing: {missing}")
        start = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(minutes=args.start_in)
        status, contest = call("POST", api + "/admin/contests", {
            "title": CONTEST_TITLE,
            "startTime": start.isoformat(),
            "endTime": (start + datetime.timedelta(minutes=args.minutes)).isoformat(),
            "penaltyMinutes": 20,
            "problems": [{"problemId": problems[slug], "label": label} for slug, label in CONTEST_PROBLEMS],
        }, admin)
        if status != 201:
            sys.exit(f"creating the contest failed: HTTP {status} {contest and contest.get('message')}")
        print(f"\nCreated contest {contest['id']}: starts {contest['startTime']}, ends {contest['endTime']}")
    print(f"  {args.base_url.rstrip('/')}/contests/{contest['id']}")
    print("\nThe tutor answers 403 for problems in a running contest; use a problem outside it "
          "(for example Fibonacci Number) for the tutor part of the demo.")


if __name__ == "__main__":
    main()
