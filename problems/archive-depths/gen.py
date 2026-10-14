def case(keys, queries):
    return f"{len(keys)} {len(queries)}\n" + " ".join(map(str, keys)) + "\n" + " ".join(map(str, queries)) + "\n"


EDGE_CASES = [
    case([7], [7]),
    case([7], [8]),
    case([1, 2, 3, 4, 5], [5, 1, 6]),
    case([5, 4, 3, 2, 1], [1, 5, 3]),
]


def small(rng):
    keys = rng.sample(range(1, 30), rng.randint(1, 10))
    return case(keys, [rng.randint(1, 30) for _ in range(rng.randint(1, 8))])


def large(rng):
    keys = rng.sample(range(1, 1000001), 2000)
    if rng.random() < 0.67:
        keys.sort(reverse=rng.random() < 0.5)
    queries = [rng.choice(keys) if rng.random() < 0.8 else rng.randint(1, 1000000) for _ in range(2000)]
    return case(keys, queries)


LARGE_COUNT = 3
