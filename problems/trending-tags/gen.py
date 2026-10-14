import string


def case(k, tags):
    return f"{len(tags)} {k}\n" + " ".join(tags) + "\n"


def vocabulary(rng, size, max_len):
    words = set()
    while len(words) < size:
        words.add("".join(rng.choice(string.ascii_lowercase) for _ in range(rng.randint(1, max_len))))
    return sorted(words)


EDGE_CASES = [
    case(1, ["solo"]),
    case(3, ["zz", "aa", "mm"]),
    case(2, ["b", "a", "b", "a", "c"]),
    case(1, ["abcdefgh"] * 5),
]


def small(rng):
    words = vocabulary(rng, rng.randint(1, 5), 2)
    tags = [rng.choice(words) for _ in range(rng.randint(1, 12))]
    return case(rng.randint(1, len(set(tags))), tags)


def large(rng):
    words = vocabulary(rng, rng.choice([50, 5000]), 8)
    tags = [rng.choice(words) for _ in range(80000)]
    return case(rng.randint(1, len(set(tags))), tags)
