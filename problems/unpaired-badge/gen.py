def case(ids):
    return f"{len(ids)}\n" + " ".join(map(str, ids)) + "\n"


def shuffled(rng, pairs, single):
    ids = [x for x in pairs for _ in range(2)] + [single]
    rng.shuffle(ids)
    return ids


EDGE_CASES = [
    case([1]),
    case([1000000000, 1, 1]),
    case([5, 5, 6, 6, 4, 4, 1000000000]),
]


def small(rng):
    pool = rng.sample(range(1, 40), rng.randint(1, 8))
    return case(shuffled(rng, pool[1:], pool[0]))


def large(rng):
    pool = rng.sample(range(1, 1000000001), 40001)
    return case(shuffled(rng, pool[1:], pool[0]))
