SLOW_SHOULD_TLE = True


def case(n, lanes):
    return f"{n} {len(lanes)}\n" + "".join(f"{a} {b} {w}\n" for a, b, w in lanes)


EDGE_CASES = [
    case(1, []),
    case(2, []),
    case(2, [(2, 1, 5)]),
    case(3, [(1, 2, 0), (2, 3, 0), (1, 3, 1)]),
    case(4, [(1, 2, 1), (2, 1, 1), (1, 1, 3), (2, 4, 1000000)]),
]


def small(rng):
    n = rng.randint(1, 8)
    lanes = [(rng.randint(1, n), rng.randint(1, n), rng.randint(0, 10)) for _ in range(rng.randint(0, 16))]
    return case(n, lanes)


def large(rng):
    n = 50000
    order = list(range(2, n))
    rng.shuffle(order)
    path = [1] + order[:20000] + [n]
    lanes = [(a, b, rng.randint(0, 1000000)) for a, b in zip(path, path[1:])]
    while len(lanes) < 50000:
        lanes.append((rng.randint(1, n), rng.randint(1, n), rng.randint(0, 1000000)))
    rng.shuffle(lanes)
    return case(n, lanes)
