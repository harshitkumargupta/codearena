import sys

data = sys.stdin.read().split()
n, q = int(data[0]), int(data[1])
alliance = list(range(n + 1))
for i in range(q):
    kind, a, b = data[2 + 3 * i], int(data[3 + 3 * i]), int(data[4 + 3 * i])
    if kind == "U":
        old, new = alliance[b], alliance[a]
        if old != new:
            for clan in range(1, n + 1):
                if alliance[clan] == old:
                    alliance[clan] = new
    else:
        print("YES" if alliance[a] == alliance[b] else "NO")
