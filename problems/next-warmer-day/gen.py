SLOW_SHOULD_TLE = True


def case(temps):
    return f"{len(temps)}\n" + " ".join(map(str, temps)) + "\n"


EDGE_CASES = [
    case([0]),
    case([-50, 50]),
    case([50, 49, 48, 47]),
    case([3, 3, 3, 4]),
]


def small(rng):
    return case([rng.randint(-5, 5) for _ in range(rng.randint(1, 15))])


def large(rng):
    n = 100000
    if rng.random() < 0.5:
        temps = sorted((rng.randint(-50, 50) for _ in range(n)), reverse=True)
    else:
        temps = [rng.randint(-50, 50) for _ in range(n)]
    return case(temps)


LARGE_COUNT = 3
