"""Helpers shared by the load-test scripts: .env parsing, the Compose command, psql and HTTP calls."""
import json
import os
import subprocess
import urllib.error
import urllib.request

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


def read_env(path):
    env = {}
    with open(path) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                env[key] = value
    return env


def compose(env_file, *args, capture=True):
    cmd = ["docker", "compose", "--env-file", env_file, "-f", "docker-compose.yml", "-f", "docker-compose.loadtest.yml",
           *args]
    return subprocess.run(cmd, cwd=REPO, check=True, text=True, capture_output=capture)


def psql(env_file, sql):
    env = read_env(env_file)
    res = compose(env_file, "exec", "-T", "postgres", "psql", "-U", env.get("POSTGRES_USER", "codearena"),
                  "-d", env.get("POSTGRES_DB", "codearena"), "-v", "ON_ERROR_STOP=1", "-At", "-F", "\t", "-c", sql)
    return [line.split("\t") for line in res.stdout.splitlines() if line]


def http(method, url, body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, method=method, headers=headers,
                                 data=None if body is None else json.dumps(body).encode())
    try:
        with urllib.request.urlopen(req, timeout=60) as res:
            raw = res.read()
            return res.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, None


def prefix(run_id):
    return f"lt{run_id}_"


def like_prefix(run_id):
    """SQL LIKE pattern for the run prefix; the underscore is escaped so it is not a wildcard."""
    return prefix(run_id).replace("_", "\\_") + "%"
