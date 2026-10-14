SLOW_SHOULD_TLE = True


def case(rows):
    return f"{len(rows)}\n" + "\n".join(f"{a} {b} {m}" for a, b, m in rows) + "\n"


EDGE_CASES = [
    case([(0, 0, 1), (5, 0, 1), (0, 0, 7)]),
    case([(2, 62, 10 ** 9), (999999999, 2, 10 ** 9), (3, 1, 3)]),
]

HEAVY_EDGE_CASES = [
    case([(10 ** 18, 10 ** 18, 10 ** 9), (10 ** 18, 1, 999999937), (1, 10 ** 18, 2)]),
]


def small(rng):
    rows = [(rng.randint(0, 50), rng.randint(0, 40), rng.randint(1, 60)) for _ in range(rng.randint(1, 6))]
    return case(rows)


def large(rng):
    rows = [(rng.randint(0, 10 ** 18), rng.randint(10 ** 17, 10 ** 18), rng.randint(1, 10 ** 9))
            for _ in range(15000)]
    return case(rows)
