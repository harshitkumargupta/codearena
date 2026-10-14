SLOW_SHOULD_TLE = True


def case(hours, volumes):
    return f"{len(volumes)} {hours}\n" + " ".join(map(str, volumes)) + "\n"


EDGE_CASES = [
    case(1, [1]),
    case(5, [7, 7, 7, 7, 7]),
]

HEAVY_EDGE_CASES = [
    case(1, [1000000000]),
    case(1000000000, [1000000000]),
]


def small(rng):
    n = rng.randint(1, 8)
    volumes = [rng.randint(1, 40) for _ in range(n)]
    return case(rng.randint(n, n + 30), volumes)


def large(rng):
    n = 80000
    volumes = [rng.randint(1, 1000000000) for _ in range(n)]
    return case(rng.choice([n, rng.randint(n, 10 * n)]), volumes)
