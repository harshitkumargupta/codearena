import sys


def main():
    data = sys.stdin.buffer.read().split()
    n, q = int(data[0]), int(data[1])
    parent = list(range(n + 1))
    size = [1] * (n + 1)

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
        ra, rb = find(a), find(b)
        if kind == b"U":
            if ra != rb:
                if size[ra] < size[rb]:
                    ra, rb = rb, ra
                parent[rb] = ra
                size[ra] += size[rb]
        else:
            out.append("YES" if ra == rb else "NO")
    sys.stdout.write("\n".join(out) + ("\n" if out else ""))


main()
