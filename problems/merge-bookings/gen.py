def case(bookings):
    return f"{len(bookings)}\n" + "".join(f"{s} {e}\n" for s, e in bookings)


EDGE_CASES = [
    case([(0, 1)]),
    case([(4, 6), (4, 6), (4, 6)]),
    case([(1, 2), (2, 3), (3, 4), (10, 11)]),
    case([(0, 50), (5, 10), (20, 30)]),
]

HEAVY_EDGE_CASES = [case([(0, 1000000000), (999999999, 1000000000), (0, 1)])]


def small(rng):
    bookings = []
    for _ in range(rng.randint(1, 8)):
        s = rng.randint(0, 40)
        bookings.append((s, s + rng.randint(1, 8)))
    return case(bookings)


def large(rng):
    bookings = []
    for _ in range(40000):
        s = rng.randint(0, 999000000)
        bookings.append((s, s + rng.randint(1, 30000)))
    return case(bookings)
