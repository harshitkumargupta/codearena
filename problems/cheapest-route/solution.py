import heapq
import sys


def main():
    data = sys.stdin.buffer.read().split()
    n, m = int(data[0]), int(data[1])
    lanes = [[] for _ in range(n + 1)]
    for i in range(m):
        a, b, w = int(data[2 + 3 * i]), int(data[3 + 3 * i]), int(data[4 + 3 * i])
        lanes[a].append((b, w))
    dist = [-1] * (n + 1)
    heap = [(0, 1)]
    while heap:
        d, node = heapq.heappop(heap)
        if dist[node] >= 0:
            continue
        dist[node] = d
        if node == n:
            break
        for other, w in lanes[node]:
            if dist[other] < 0:
                heapq.heappush(heap, (d + w, other))
    print(dist[n])


main()
