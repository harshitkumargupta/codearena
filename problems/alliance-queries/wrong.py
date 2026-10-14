import sys

data = sys.stdin.read().split()
n, q = int(data[0]), int(data[1])
parent = list(range(n + 1))


def find(x):
    root = x
    while parent[root] != root:
        root = parent[root]
    while parent[x] != root:
        parent[x], x = root, parent[x]
    return root


out = []
for i in range(q):
    kind, a, b = data[2 + 3 * i], int(data[3 + 3 * i]), int(data[4 + 3 * i])
    if kind == "U":
        parent[a] = find(b)
    else:
        out.append("YES" if find(a) == find(b) else "NO")
print("\n".join(out))
