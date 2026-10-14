def case(grid):
    return f"{len(grid)} {len(grid[0])}\n" + "\n".join(grid) + "\n"


def random_grid(rng, rows, cols, wall):
    cells = [["#" if rng.random() < wall else "." for _ in range(cols)] for _ in range(rows)]
    spots = rng.sample(range(rows * cols), 2)
    for spot, mark in zip(spots, "ST"):
        cells[spot // cols][spot % cols] = mark
    return case(["".join(row) for row in cells])


def maze(rng, rows, cols):
    cells = [["." for _ in range(cols)] for _ in range(rows)]
    for r in range(1, rows, 2):
        gap = rng.randrange(cols)
        for c in range(cols):
            if c != gap:
                cells[r][c] = "#"
    cells[0][0] = "S"
    cells[rows - 1][cols - 1] = "T"
    return case(["".join(row) for row in cells])


EDGE_CASES = [
    case(["ST"]),
    case(["S", "#", "T"]),
    case(["S.", ".T"]),
    case(["S#.", "##.", "..T"]),
]


def small(rng):
    rows, cols = rng.randint(1, 7), rng.randint(1, 7)
    if rows * cols < 2:
        cols = 2
    return random_grid(rng, rows, cols, rng.uniform(0, 0.4))


def large(rng):
    if rng.random() < 0.5:
        return maze(rng, 500, 500)
    return random_grid(rng, 500, 500, 0.3)


LARGE_COUNT = 3
