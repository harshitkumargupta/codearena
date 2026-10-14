"""Problem packs: loading, statement checks, deterministic test-set building and running the references.

A pack is a directory problems/<slug>/ with problem.yaml, statement.md, solution.py, Main.java, brute.py, gen.py,
samples/1.in and samples/2.in, and optionally wrong.py. Expected outputs always come from solution.py.

gen.py defines EDGE_CASES (hand-written inputs), small(rng) and large(rng), and optionally HEAVY_EDGE_CASES
(hand-written inputs too extreme for brute.py), LARGE_COUNT (2 or 3) and SLOW_SHOULD_TLE (brute.py must time out
in the live judge).
"""
import importlib.util
import math
import os
import random
import re
import shutil
import subprocess
import tempfile
import time

REPO = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
PROBLEMS_DIR = os.path.join(REPO, "problems")

MAX_CASE_BYTES = 1024 * 1024
MAX_PROBLEM_BYTES = 4_000_000
MIN_HIDDEN, MAX_HIDDEN = 8, 15
MIN_LARGE, MAX_LARGE = 2, 3
CROSS_CHECK_CASES = 160
TARGET_HIDDEN = 12
TIME_FACTOR = 8
MIN_TIME_LIMIT_MS = 2000
MAX_TIME_LIMIT_MS = 10_000
DIFFICULTIES = ("EASY", "MEDIUM", "HARD")
REQUIRED_FIELDS = ("title", "slug", "difficulty", "topic", "time_limit_ms", "memory_limit_mb")
SECTIONS = ("Input", "Output", "Constraints")
SEEDED_SLUGS = ("two-sum", "fizzbuzz", "valid-parentheses", "reverse-string", "fibonacci", "maximum-subarray")
SLUG_PATTERN = re.compile(r"^[a-z0-9]+(-[a-z0-9]+)*$")


class PackError(Exception):
    pass


def python_cmd():
    found = os.environ.get("CODEARENA_PYTHON") or shutil.which("python3.12")
    if not found:
        raise PackError("python3.12 not found (the judge runs Python 3.12); set CODEARENA_PYTHON")
    return found


def read_meta(path):
    """problem.yaml is flat `key: value` lines; values are strings or integers."""
    meta = {}
    with open(path, encoding="utf-8") as f:
        for n, line in enumerate(f, 1):
            line = line.rstrip("\n")
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            if ":" not in line:
                raise PackError(f"{path}:{n}: expected `key: value`")
            key, value = (part.strip() for part in line.split(":", 1))
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            meta[key] = int(value) if re.fullmatch(r"-?\d+", value) else value
    return meta


def write_time_limit(path, time_limit_ms):
    with open(path, encoding="utf-8") as f:
        lines = f.read().splitlines()
    lines = [f"time_limit_ms: {time_limit_ms}" if l.startswith("time_limit_ms:") else l for l in lines]
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


class Pack:
    def __init__(self, directory):
        self.dir = os.path.abspath(directory)
        self.slug = os.path.basename(self.dir)
        self.meta = read_meta(self.path("problem.yaml"))
        with open(self.path("statement.md"), encoding="utf-8") as f:
            self.statement = f.read()

    def path(self, *parts):
        return os.path.join(self.dir, *parts)

    def has(self, name):
        return os.path.exists(self.path(name))

    def read(self, name):
        with open(self.path(name), encoding="utf-8") as f:
            return f.read()

    def generator(self):
        spec = importlib.util.spec_from_file_location(f"gen_{self.slug.replace('-', '_')}", self.path("gen.py"))
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        for name in ("EDGE_CASES", "small", "large"):
            if not hasattr(module, name):
                raise PackError(f"{self.slug}: gen.py must define {name}")
        return module

    def samples(self):
        names = sorted(n for n in os.listdir(self.path("samples")) if n.endswith(".in"))
        return [self.read(os.path.join("samples", n)) for n in names]


def all_packs(root=PROBLEMS_DIR):
    return [Pack(os.path.join(root, d)) for d in sorted(os.listdir(root))
            if os.path.isfile(os.path.join(root, d, "problem.yaml"))]


def check_metadata(pack):
    errors = []
    for field in REQUIRED_FIELDS:
        if field not in pack.meta or pack.meta[field] in ("", None):
            errors.append(f"problem.yaml: missing {field}")
    if errors:
        return errors
    if pack.meta["slug"] != pack.slug:
        errors.append(f"problem.yaml: slug {pack.meta['slug']!r} does not match the directory {pack.slug!r}")
    if not SLUG_PATTERN.match(pack.slug):
        errors.append("slug must be lowercase words separated by hyphens")
    if pack.slug in SEEDED_SLUGS:
        errors.append("slug clashes with a seeded problem")
    if pack.meta["difficulty"] not in DIFFICULTIES:
        errors.append(f"problem.yaml: difficulty must be one of {DIFFICULTIES}")
    if not isinstance(pack.meta["time_limit_ms"], int) or not isinstance(pack.meta["memory_limit_mb"], int):
        errors.append("problem.yaml: time_limit_ms and memory_limit_mb must be integers")
    elif not 32 <= pack.meta["memory_limit_mb"] <= 1024:
        errors.append("problem.yaml: memory_limit_mb must be 32..1024")
    for name in ("statement.md", "solution.py", "Main.java", "brute.py", "gen.py"):
        if not pack.has(name):
            errors.append(f"missing {name}")
    if pack.has("samples"):
        count = len([n for n in os.listdir(pack.path("samples")) if n.endswith(".in")])
        if count != 2:
            errors.append(f"samples/ must hold exactly 2 .in files, found {count}")
    else:
        errors.append("missing samples/")
    return errors


def check_statement(text):
    """Description first, then ### Input, ### Output, ### Constraints in that order; constraints must give bounds."""
    errors = []
    headings = re.findall(r"^### (.+?)\s*$", text, flags=re.M)
    positions = []
    for section in SECTIONS:
        if section not in headings:
            errors.append(f"statement.md: missing section ### {section}")
        else:
            positions.append(headings.index(section))
    if not errors and positions != sorted(positions):
        errors.append("statement.md: sections must be in the order Input, Output, Constraints")
    first = re.search(r"^### ", text, flags=re.M)
    if not first or not text[:first.start()].strip():
        errors.append("statement.md: missing the description before ### Input")
    constraints = re.search(r"^### Constraints\s*$(.*?)(?=^### |\Z)", text, flags=re.M | re.S)
    if constraints:
        bullets = [l for l in constraints.group(1).splitlines() if l.strip().startswith("- ")]
        if not bullets or not any(re.search(r"\d", b) for b in bullets):
            errors.append("statement.md: ### Constraints must list bounds as `- ` bullets with numbers")
    return errors


def rng_for(slug, tier, index):
    return random.Random(f"{slug}:{tier}:{index}")


class TestSet:
    def __init__(self, samples, hidden, cross_check, large_indices, no_brute_indices):
        self.samples = samples
        self.hidden = hidden
        self.cross_check = cross_check
        self.large_indices = large_indices
        self.no_brute_indices = no_brute_indices


def build_inputs(pack, cross_check_cases=CROSS_CHECK_CASES):
    """Deterministic inputs: samples, hidden (edge cases, small random, heavy edge cases, 2-3 max-size) and
    cross-check cases. brute.py is not run on HEAVY_EDGE_CASES (extreme values) or on the max-size cases."""
    gen = pack.generator()
    large_count = getattr(gen, "LARGE_COUNT", 2)
    edge = list(gen.EDGE_CASES)
    heavy = list(getattr(gen, "HEAVY_EDGE_CASES", []))
    small_hidden = max(1, TARGET_HIDDEN - len(edge) - len(heavy) - large_count)
    hidden = edge + [gen.small(rng_for(pack.slug, "hidden", i)) for i in range(small_hidden)] + heavy
    large_start = len(hidden)
    hidden += [gen.large(rng_for(pack.slug, "large", i)) for i in range(large_count)]
    cross = [gen.small(rng_for(pack.slug, "check", i)) for i in range(cross_check_cases)]
    return TestSet(pack.samples(), hidden, cross, list(range(large_start, len(hidden))),
                   list(range(large_start - len(heavy), len(hidden))))


class Runner:
    """Runs a pack's Python files and its compiled Main.java on one input."""

    def __init__(self, pack, timeout=30):
        self.pack = pack
        self.timeout = timeout
        self.python = python_cmd()
        self._classes = None

    def compile_java(self, source_name="Main.java"):
        out = tempfile.mkdtemp(prefix=f"java-{self.pack.slug}-")
        shutil.copy(self.pack.path(source_name), os.path.join(out, "Main.java"))
        res = subprocess.run(["javac", "-d", out, os.path.join(out, "Main.java")], capture_output=True, text=True)
        if res.returncode != 0:
            raise PackError(f"{self.pack.slug}: Main.java does not compile:\n{res.stderr[-2000:]}")
        self._classes = out
        return out

    def close(self):
        if self._classes:
            shutil.rmtree(self._classes, ignore_errors=True)

    def _run(self, cmd, data):
        started = time.perf_counter()
        res = subprocess.run(cmd, input=data.encode(), capture_output=True, timeout=self.timeout)
        elapsed = time.perf_counter() - started
        if res.returncode != 0:
            raise PackError(f"{' '.join(os.path.basename(c) for c in cmd[-2:])} exited {res.returncode}: "
                            f"{res.stderr.decode(errors='replace')[-500:]}")
        return res.stdout.decode(), elapsed

    def python_file(self, name, data):
        return self._run([self.python, self.pack.path(name)], data)

    def java(self, data):
        if not self._classes:
            self.compile_java()
        # Same flags as the judge's run command (SandboxRunner), minus the heap limit.
        return self._run(["java", "-Xss64m", "-XX:+UseSerialGC", "-XX:-UsePerfData", "-cp", self._classes, "Main"],
                         data)


def normalize(output):
    """The judge's comparison: trailing whitespace per line and trailing blank lines are ignored."""
    lines = [l.rstrip() for l in output.replace("\r\n", "\n").split("\n")]
    while lines and lines[-1] == "":
        lines.pop()
    return "\n".join(lines)


def required_time_limit(slowest_seconds):
    ms = math.ceil(slowest_seconds * 1000 * TIME_FACTOR / 500) * 500
    return max(MIN_TIME_LIMIT_MS, ms)
