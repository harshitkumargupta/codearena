import sys

data = sys.stdin.read().split()
n, m = int(data[0]), int(data[1])
lanes = [(int(data[2 + 3 * i]), int(data[3 + 3 * i]), int(data[4 + 3 * i])) for i in range(m)]
INF = float("inf")
dist = [INF] * (n + 1)
dist[1] = 0
for _ in range(n - 1):
    for a, b, w in lanes:
        if dist[a] + w < dist[b]:
            dist[b] = dist[a] + w
print(-1 if dist[n] == INF else dist[n])
