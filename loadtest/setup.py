"""Creates the run's test users (and optionally a contest they all join) through the real api.

Every username and the contest title start with lt<runid>_, which is the only thing cleanup.py deletes.
Credentials go to loadtest/.runs/<runid>/users.json (git-ignored); nothing secret is printed.
"""
import argparse
import datetime
import json
import os
import secrets

from common import REPO, http, prefix, read_env


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run-id", required=True)
    ap.add_argument("--users", type=int, required=True)
    ap.add_argument("--contest", action="store_true")
    ap.add_argument("--base-url", default="http://localhost:8080")
    ap.add_argument("--env-file", default=os.path.join(REPO, ".env"))
    args = ap.parse_args()

    api = args.base_url + "/api"
    password = secrets.token_urlsafe(18)
    users = []
    for i in range(args.users):
        name = f"{prefix(args.run_id)}u{i:03d}"
        status, body = http("POST", api + "/auth/register",
                            {"username": name, "email": f"{name}@loadtest.invalid", "password": password})
        if status != 201:
            raise SystemExit(f"register {name} failed: HTTP {status} {body and body.get('message')}")
        users.append({"username": name, "password": password})

    contest_id = None
    if args.contest:
        env = read_env(args.env_file)
        status, body = http("POST", api + "/auth/login",
                            {"username": env["ADMIN_USERNAME"], "password": env["ADMIN_PASSWORD"]})
        if status != 200:
            raise SystemExit(f"admin login failed: HTTP {status}")
        admin = body["accessToken"]
        problems = {p["slug"]: p["id"] for p in http("GET", api + "/problems?size=50")[1]["content"]}
        now = datetime.datetime.now(datetime.timezone.utc)
        status, body = http("POST", api + "/admin/contests", {
            "title": f"{prefix(args.run_id)}contest",
            "startTime": (now - datetime.timedelta(minutes=1)).isoformat(),
            "endTime": (now + datetime.timedelta(hours=3)).isoformat(),
            "penaltyMinutes": 20,
            "problems": [{"problemId": problems["two-sum"], "label": "A"},
                         {"problemId": problems["fizzbuzz"], "label": "B"},
                         {"problemId": problems["reverse-string"], "label": "C"}],
        }, admin)
        if status != 201:
            raise SystemExit(f"contest creation failed: HTTP {status} {body and body.get('message')}")
        contest_id = body["id"]
        for u in users:
            token = http("POST", api + "/auth/login", u)[1]["accessToken"]
            status, _ = http("POST", f"{api}/contests/{contest_id}/join", token=token)
            if status not in (200, 201):
                raise SystemExit(f"join failed for {u['username']}: HTTP {status}")

    run_dir = os.path.join(REPO, "loadtest", ".runs", args.run_id)
    os.makedirs(run_dir, exist_ok=True)
    path = os.path.join(run_dir, "users.json")
    with open(path, "w") as f:
        json.dump({"runId": args.run_id, "contestId": contest_id, "users": users}, f)
    os.chmod(path, 0o600)
    print(f"setup {args.run_id}: {len(users)} users created" + (f", contest {contest_id} joined by all" if contest_id else ""))


if __name__ == "__main__":
    main()
