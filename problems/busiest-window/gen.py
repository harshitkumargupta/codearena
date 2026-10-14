SLOW_SHOULD_TLE = True


def case(k, counts):
    return f"{len(counts)} {k}\n" + " ".join(map(str, counts)) + "\n"


EDGE_CASES = [
    case(1, [0]),
    case(1, [3, 9999, 2]),
    case(5, [9999] * 5),
    case(2, [0, 0, 0, 0, 7]),
]


def small(rng):
    n = rng.randint(1, 15)
    return case(rng.randint(1, n), [rng.randint(0, 30) for _ in range(n)])


def large(rng):
    n = 150000
    return case(rng.randint(n // 3, 2 * n // 3), [rng.randint(0, 9999) for _ in range(n)])
