def case(limit, weights):
    return f"{len(weights)} {limit}\n" + " ".join(map(str, weights)) + "\n"


EDGE_CASES = [
    case(1, [1]),
    case(10, [5, 5, 5, 5, 5]),
    case(30000, [30000, 1, 29999, 15000, 15000]),
    case(9, [8, 1, 7, 2, 9, 1]),
]


def small(rng):
    limit = rng.randint(1, 20)
    return case(limit, [rng.randint(1, limit) for _ in range(rng.randint(1, 10))])


def large(rng):
    limit = rng.randint(20000, 30000)
    return case(limit, [rng.randint(1, limit) for _ in range(100000)])
