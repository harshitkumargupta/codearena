"""Deletes only what setup.py created for one run: users named lt<runid>_*, their submissions and contest rows,
the contest titled lt<runid>_*, and their Redis keys. Always prints the matched row counts first; deletes only
with --yes."""
import argparse
import os

from common import REPO, compose, like_prefix, prefix, psql, read_env

COUNTS = """
with u as (select id from users where username like '{p}' escape '\\'),
     c as (select id from contests where title like '{p}' escape '\\')
select 'users', count(*) from u
union all select 'contests', count(*) from c
union all select 'submissions', count(*) from submissions where user_id in (select id from u) or contest_id in (select id from c)
union all select 'contest_participants', count(*) from contest_participants where user_id in (select id from u) or contest_id in (select id from c)
union all select 'contest_problems', count(*) from contest_problems where contest_id in (select id from c)
union all select 'hint_logs', count(*) from hint_logs where user_id in (select id from u)
"""

DELETE = """
begin;
create temp table lt_users as select id from users where username like '{p}' escape '\\';
create temp table lt_contests as select id from contests where title like '{p}' escape '\\';
delete from hint_logs where user_id in (select id from lt_users)
    or submission_id in (select id from submissions where user_id in (select id from lt_users));
delete from submissions where user_id in (select id from lt_users) or contest_id in (select id from lt_contests);
delete from contest_participants where user_id in (select id from lt_users) or contest_id in (select id from lt_contests);
delete from contests where id in (select id from lt_contests);
delete from users where id in (select id from lt_users);
commit;
"""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--run-id", required=True)
    ap.add_argument("--env-file", default=os.path.join(REPO, ".env"))
    ap.add_argument("--yes", action="store_true", help="delete; without it only the counts are printed")
    args = ap.parse_args()
    if not args.run_id or not args.run_id.isalnum():
        raise SystemExit("run id must be alphanumeric")
    pattern = like_prefix(args.run_id)

    counts = psql(args.env_file, COUNTS.format(p=pattern))
    print(f"rows matched by prefix {prefix(args.run_id)}:")
    for table, n in counts:
        print(f"  {table:22} {n}")
    user_ids = [r[0] for r in psql(args.env_file, f"select id from users where username like '{pattern}' escape '\\'")]
    contest_ids = [r[0] for r in psql(args.env_file,
                                      f"select id from contests where title like '{pattern}' escape '\\'")]
    keys = [f"rl:submissions:{i}" for i in user_ids] + [f"rl:tutor:{i}" for i in user_ids] \
        + [f"contest:{c}:lb" for c in contest_ids]
    print(f"  redis keys (candidates) {len(keys)}")
    if not args.yes:
        print("dry run: nothing deleted")
        return

    psql(args.env_file, DELETE.format(p=pattern))
    deleted = 0
    for i in range(0, len(keys), 200):
        res = compose(args.env_file, "exec", "-T", "redis", "redis-cli", "del", *keys[i:i + 200])
        deleted += int(res.stdout.strip() or 0)
    remaining = psql(args.env_file, COUNTS.format(p=pattern))
    print(f"deleted; redis keys removed {deleted}; remaining rows: "
          + ", ".join(f"{t}={n}" for t, n in remaining))


if __name__ == "__main__":
    main()
