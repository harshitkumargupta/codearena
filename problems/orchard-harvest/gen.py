SLOW_SHOULD_TLE = True


def case(grid):
    return f"{len(grid)} {len(grid[0])}\n" + "\n".join(grid) + "\n"


def random_grid(rng, rows, cols, pond, digits="0123456789"):
    return case(["".join("#" if rng.random() < pond else rng.choice(digits) for _ in range(cols))
                 for _ in range(rows)])


EDGE_CASES = [
    case(["7"]),
    case(["#"]),
    case(["0000", "0000", "0000"]),
    case(["5#", "#5"]),
    case(["11", "11"]),
]


def small(rng):
    return random_grid(rng, rng.randint(1, 6), rng.randint(1, 6), rng.uniform(0, 0.3), rng.choice(["01", "0123456789"]))


def large(rng):
    if rng.random() < 0.5:
        return random_grid(rng, 600, 600, 0.0, "0")
    return random_grid(rng, 600, 600, 0.05, "019")
