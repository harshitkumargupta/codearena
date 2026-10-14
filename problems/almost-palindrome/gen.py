def line(s):
    return s + "\n"


EDGE_CASES = [line("a"), line("ab"), line("cuucu"), line("abcba"), line("abccdba")]


def palindrome(rng, half, letters):
    left = "".join(rng.choice(letters) for _ in range(half))
    middle = rng.choice(["", rng.choice(letters)])
    return left + middle + left[::-1]


def variant(rng, n, letters):
    s = list(palindrome(rng, n // 2, letters)) or [rng.choice(letters)]
    kind = rng.randint(0, 3)
    if kind == 1:
        s.insert(rng.randint(0, len(s)), rng.choice(letters))
    elif kind == 2:
        for _ in range(2):
            s[rng.randrange(len(s))] = rng.choice(letters)
    elif kind == 3:
        s = [rng.choice(letters) for _ in range(len(s))]
    return "".join(s)


def small(rng):
    return line(variant(rng, rng.randint(1, 10), "ab" if rng.random() < 0.5 else "abc"))


def large(rng):
    s = list(palindrome(rng, 99999, "xy"))
    s.insert(rng.randint(0, len(s)), "x")
    return line("".join(s))
