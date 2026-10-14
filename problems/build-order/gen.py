SLOW_SHOULD_TLE = True


def case(n, rules):
    return f"{n} {len(rules)}\n" + "".join(f"{a} {b}\n" for a, b in rules)


def dag(rng, n, m):
    rank = list(range(1, n + 1))
    rng.shuffle(rank)
    rules = []
    for _ in range(m):
        i, j = rng.randrange(n), rng.randrange(n)
        if i != j:
            rules.append((rank[min(i, j)], rank[max(i, j)]))
    return rules


EDGE_CASES = [
    case(1, []),
    case(1, [(1, 1)]),
    case(3, [(3, 2), (3, 2), (2, 1)]),
    case(4, []),
    case(4, [(1, 2), (2, 3), (3, 4), (4, 2)]),
]


def small(rng):
    n = rng.randint(1, 8)
    rules = dag(rng, n, rng.randint(0, 10))
    if rng.random() < 0.2 and n > 1:
        rules.append((rng.randint(1, n), rng.randint(1, n)))
    rng.shuffle(rules)
    return case(n, rules)


def large(rng):
    n = 100000
    rules = dag(rng, n, 70000)
    if rng.random() < 0.34:
        a, b = rules[0]
        rules[-1] = (b, a)
    rng.shuffle(rules)
    return case(n, rules)


LARGE_COUNT = 3
