def case(head_first_order, labels):
    n = len(labels)
    behind = [0] * (n + 1)
    for a, b in zip(head_first_order, head_first_order[1:]):
        behind[a] = b
    lines = [f"{n} {head_first_order[0]}"] + [f"{labels[i - 1]} {behind[i]}" for i in range(1, n + 1)]
    return "\n".join(lines) + "\n"


EDGE_CASES = [
    case([1], [1000000]),
    case([1, 2, 3, 4, 5], [5, 4, 3, 2, 1]),
    case([5, 4, 3, 2, 1], [9, 9, 9, 9, 9]),
    case([2, 1], [1, 2]),
]


def random_case(rng, n, max_label):
    order = list(range(1, n + 1))
    rng.shuffle(order)
    return case(order, [rng.randint(1, max_label) for _ in range(n)])


def small(rng):
    return random_case(rng, rng.randint(1, 12), 30)


def large(rng):
    return random_case(rng, 60000, 1000000)
