import heapq
import sys


def main():
    data = sys.stdin.buffer.read().split()
    n, m = int(data[0]), int(data[1])
    after = [[] for _ in range(n + 1)]
    waiting = [0] * (n + 1)
    for i in range(m):
        a, b = int(data[2 + 2 * i]), int(data[3 + 2 * i])
        after[a].append(b)
        waiting[b] += 1
    ready = [t for t in range(1, n + 1) if waiting[t] == 0]
    heapq.heapify(ready)
    order = []
    while ready:
        task = heapq.heappop(ready)
        order.append(task)
        for nxt in after[task]:
            waiting[nxt] -= 1
            if waiting[nxt] == 0:
                heapq.heappush(ready, nxt)
    print(" ".join(map(str, order)) if len(order) == n else "IMPOSSIBLE")


main()
