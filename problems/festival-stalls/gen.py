def case(income):
    return f"{len(income)}\n" + " ".join(map(str, income)) + "\n"


EDGE_CASES = [
    case([0]),
    case([10000]),
    case([4, 1, 1, 9]),
    case([0, 0, 0, 0, 0, 0]),
]


def small(rng):
    return case([rng.randint(0, 20) for _ in range(rng.randint(1, 14))])


def large(rng):
    return case([rng.randint(0, 10000) for _ in range(100000)])
