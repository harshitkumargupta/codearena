SLOW_SHOULD_TLE = True


def case(w, times):
    return f"{len(times)} {w}\n" + " ".join(map(str, times)) + "\n"


def walk(rng, n, max_step, start=0):
    times, t = [], start
    for _ in range(n):
        t += rng.randint(0, max_step)
        times.append(t)
    return times


EDGE_CASES = [
    case(1, [0]),
    case(100000000, [0, 50000000, 100000000]),
    case(10, [5, 14, 15, 24, 25, 25]),
    case(3, [0, 0, 0, 1, 2, 3, 4]),
]


def small(rng):
    return case(rng.randint(1, 20), walk(rng, rng.randint(1, 12), 8))


def large(rng):
    n = 80000
    times = walk(rng, n, 1250)
    return case(rng.choice([times[-1] + 1, rng.randint(1000, 1000000)]), times)
