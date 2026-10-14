def case(rain, queries):
    lines = [f"{len(rain)} {len(queries)}", " ".join(map(str, rain))]
    lines += [f"{l} {r}" for l, r in queries]
    return "\n".join(lines) + "\n"


EDGE_CASES = [
    case([0], [(1, 1)]),
    case([10000] * 6, [(1, 6), (6, 6), (1, 1)]),
    case([0, 0, 0, 5], [(1, 3), (1, 4), (4, 4), (2, 4)]),
]


def small(rng):
    n = rng.randint(1, 12)
    rain = [rng.randint(0, 20) for _ in range(n)]
    queries = []
    for _ in range(rng.randint(1, 10)):
        l = rng.randint(1, n)
        queries.append((l, rng.randint(l, n)))
    return case(rain, queries)


def large(rng):
    n = q = 40000
    rain = [rng.randint(0, 10000) for _ in range(n)]
    queries = []
    for _ in range(q):
        l = rng.randint(1, n)
        queries.append((l, rng.randint(l, n)))
    return case(rain, queries)
