SLOW_SHOULD_TLE = True


def case(heights):
    return f"{len(heights)}\n" + " ".join(map(str, heights)) + "\n"


EDGE_CASES = [
    case([1]),
    case([3, 2, 1]),
    case([1, 2, 3, 4, 5]),
    case([2, 2, 3, 3, 1, 1]),
]


def small(rng):
    return case([rng.randint(1, rng.choice([3, 10, 30])) for _ in range(rng.randint(1, 14))])


def large(rng):
    n = 100000
    kind = rng.randint(0, 2)
    if kind == 0:
        return case([rng.randint(1, 1000000) for _ in range(n)])
    if kind == 1:
        return case([rng.randint(1, 50) for _ in range(n)])
    return case(sorted(rng.randint(1, 1000000) for _ in range(n)))


LARGE_COUNT = 3
