import os
import shutil
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from pack import Pack, build_inputs, check_statement  # noqa: E402
from validate import validate  # noqa: E402

STATEMENT = """Add up the numbers on the line.

### Input
The first line contains `n`, the second `n` integers.

### Output
Print their sum.

### Constraints
- `1 <= n <= 1000`
"""

SOLUTION = "import sys\nd = sys.stdin.read().split()\nprint(sum(map(int, d[1:])))\n"
WRONG = "import sys\nd = sys.stdin.read().split()\nprint(sum(map(int, d[1:])) + (1 if len(d) > 4 else 0))\n"
BRUTE = "import sys\nd = sys.stdin.read().split()\nt = 0\nfor x in d[1:]:\n    t += int(x)\nprint(t)\n"
JAVA = """import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int n = in.nextInt();
        long total = 0;
        for (int i = 0; i < n; i++) {
            total += in.nextLong();
        }
        System.out.println(total);
    }
}
"""
GEN = """def case(values):
    return f"{len(values)}\\n" + " ".join(map(str, values)) + "\\n"


EDGE_CASES = [case([0]), case([-5, 5]), case([7] * 9)]


def small(rng):
    return case([rng.randint(-50, 50) for _ in range(rng.randint(1, 8))])


def large(rng):
    return case([rng.randint(-1000, 1000) for _ in range(SIZE)])


SIZE = 1000
"""


def make_pack(root, slug="sum-line", solution=SOLUTION, statement=STATEMENT, gen=GEN):
    d = os.path.join(root, slug)
    os.makedirs(os.path.join(d, "samples"))
    files = {"problem.yaml": f"title: Sum Line\nslug: {slug}\ndifficulty: EASY\ntopic: test\ntime_limit_ms: 2000\n"
                             "memory_limit_mb: 256\n",
             "statement.md": statement, "solution.py": solution, "brute.py": BRUTE, "Main.java": JAVA, "gen.py": gen,
             "samples/1.in": "3\n1 2 3\n", "samples/2.in": "1\n-4\n"}
    for name, text in files.items():
        with open(os.path.join(d, name), "w") as f:
            f.write(text)
    return Pack(d)


class ToolingTest(unittest.TestCase):

    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="packs-")

    def tearDown(self):
        shutil.rmtree(self.root, ignore_errors=True)

    def test_correct_pack_passes(self):
        result = validate(make_pack(self.root), cross_check_cases=20, measure=False)
        self.assertEqual(result.errors, [])
        self.assertEqual(result.stats["cross_check"], "20/20")

    def test_wrong_reference_is_caught(self):
        result = validate(make_pack(self.root, solution=WRONG), cross_check_cases=40, measure=False)
        self.assertFalse(result.ok)
        self.assertTrue(any(e.startswith("cross-check:") for e in result.errors), result.errors)

    def test_missing_section_is_caught(self):
        statement = STATEMENT.split("### Constraints")[0]
        result = validate(make_pack(self.root, statement=statement), cross_check_cases=5, measure=False)
        self.assertIn("statement.md: missing section ### Constraints", result.errors)

    def test_constraints_without_bounds_are_caught(self):
        errors = check_statement(STATEMENT.replace("- `1 <= n <= 1000`", "Small."))
        self.assertTrue(any("Constraints must list bounds" in e for e in errors), errors)

    def test_oversized_test_is_caught(self):
        gen = GEN.replace("SIZE = 1000", "SIZE = 300_000")
        result = validate(make_pack(self.root, gen=gen), cross_check_cases=5, measure=False)
        self.assertTrue(any("input is" in e and "bytes" in e for e in result.errors), result.errors)

    def test_generator_is_deterministic(self):
        pack = make_pack(self.root, slug="sum-a")
        first = build_inputs(pack, cross_check_cases=30)
        second = build_inputs(Pack(pack.dir), cross_check_cases=30)
        self.assertEqual(first.samples + first.hidden, second.samples + second.hidden)
        self.assertEqual(first.cross_check, second.cross_check)
        other = build_inputs(make_pack(self.root, slug="sum-b"), cross_check_cases=30)
        self.assertNotEqual(first.cross_check, other.cross_check, "different slugs must get different seeds")

if __name__ == "__main__":
    unittest.main()
