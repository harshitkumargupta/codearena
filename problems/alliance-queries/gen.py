SLOW_SHOULD_TLE = True


def case(n, events):
    return f"{n} {len(events)}\n" + "".join(f"{k} {a} {b}\n" for k, a, b in events)


def random_events(rng, n, q, union_share):
    events = []
    for _ in range(q):
        kind = "U" if rng.random() < union_share else "Q"
        events.append((kind, rng.randint(1, n), rng.randint(1, n)))
    return events


EDGE_CASES = [
    case(1, [("Q", 1, 1)]),
    case(2, [("U", 1, 1), ("Q", 1, 2), ("U", 2, 1), ("Q", 1, 2), ("U", 1, 2)]),
    case(4, [("U", 1, 2), ("U", 3, 4), ("U", 2, 3), ("Q", 1, 4), ("Q", 4, 1)]),
]


def small(rng):
    n = rng.randint(1, 8)
    events = random_events(rng, n, rng.randint(1, 12), 0.5)
    if all(k == "U" for k, _, _ in events):
        events.append(("Q", rng.randint(1, n), rng.randint(1, n)))
    return case(n, events)


def large(rng):
    n = 100000
    if rng.random() < 0.5:
        chain = [("U", i, i + 1) for i in range(1, 30001)]
        queries = [("Q", rng.randint(1, n), rng.randint(1, n)) for _ in range(30000)]
        return case(n, chain + queries)
    return case(n, random_events(rng, n, 60000, 0.5))


LARGE_COUNT = 3
