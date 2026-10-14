def case(n, pairs):
    return f"{n} {len(pairs)}\n" + "".join(f"{a} {b}\n" for a, b in pairs)


def random_pairs(rng, n, density):
    pairs = [(a, b) for a in range(1, n + 1) for b in range(a + 1, n + 1) if rng.random() < density]
    rng.shuffle(pairs)
    return [(b, a) if rng.random() < 0.5 else (a, b) for a, b in pairs]


EDGE_CASES = [
    case(1, []),
    case(2, [(1, 2)]),
    case(3, [(1, 2), (2, 3), (1, 3)]),
    case(9, []),
]


def small(rng):
    n = rng.randint(1, 6)
    return case(n, random_pairs(rng, n, rng.random()))


def large(rng):
    return case(9, random_pairs(rng, 9, rng.uniform(0.05, 0.2)))
