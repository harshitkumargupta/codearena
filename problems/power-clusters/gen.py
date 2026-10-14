def case(n, cables):
    return f"{n} {len(cables)}\n" + "".join(f"{a} {b}\n" for a, b in cables)


EDGE_CASES = [
    case(1, []),
    case(1, [(1, 1), (1, 1)]),
    case(4, [(1, 2), (2, 1), (3, 3)]),
    case(5, [(1, 2), (2, 3), (3, 4), (4, 5)]),
]


def small(rng):
    n = rng.randint(1, 12)
    return case(n, [(rng.randint(1, n), rng.randint(1, n)) for _ in range(rng.randint(0, 12))])


def large(rng):
    n = 100000
    if rng.random() < 0.5:
        order = list(range(1, n + 1))
        rng.shuffle(order)
        cables = [(order[i], order[i + 1]) for i in range(69999)]
        rng.shuffle(cables)
        return case(n, cables)
    return case(n, [(rng.randint(1, n), rng.randint(1, n)) for _ in range(70000)])
