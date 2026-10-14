def case(capacity, snacks):
    return f"{len(snacks)} {capacity}\n" + "".join(f"{w} {e}\n" for w, e in snacks)


EDGE_CASES = [
    case(1, [(1, 0)]),
    case(1, [(2, 5)]),
    case(10, [(10, 7), (5, 4), (5, 4)]),
    case(6, [(3, 5), (3, 5), (4, 9)]),
]


def small(rng):
    capacity = rng.randint(1, 30)
    snacks = [(rng.randint(1, 15), rng.randint(0, 40)) for _ in range(rng.randint(1, 12))]
    return case(capacity, snacks)


def large(rng):
    snacks = [(rng.randint(1, 10000), rng.randint(0, 1000000)) for _ in range(100)]
    return case(10000, snacks)
