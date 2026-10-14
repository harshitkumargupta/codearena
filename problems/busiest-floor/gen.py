from collections import deque


def serialize(values, left, right):
    tokens, queue = [], deque([0])
    tokens.append(str(values[0]))
    while queue:
        node = queue.popleft()
        for child in (left[node], right[node]):
            if child is None:
                tokens.append("x")
            else:
                tokens.append(str(values[child]))
                queue.append(child)
    while tokens[-1] == "x":
        tokens.pop()
    return f"{len(tokens)}\n" + " ".join(tokens) + "\n"


def random_tree(rng, n, low, high, chain=False):
    values = [rng.randint(low, high) for _ in range(n)]
    left, right = [None] * n, [None] * n
    for node in range(1, n):
        while True:
            parent = node - 1 if chain else rng.randrange(node)
            side = left if (chain or rng.random() < 0.5) else right
            if side[parent] is None:
                side[parent] = node
                break
            other = right if side is left else left
            if other[parent] is None:
                other[parent] = node
                break
    return serialize(values, left, right)


EDGE_CASES = [
    "1\n-10000\n",
    "3\n5 x 5\n",
    "3\n-1 -1 -1\n",
    "7\n0 0 0 0 0 0 0\n",
]


def small(rng):
    return random_tree(rng, rng.randint(1, 12), -3, 3)


def large(rng):
    chain = rng.random() < 0.5
    return random_tree(rng, 60000, -10000, 10000, chain)
