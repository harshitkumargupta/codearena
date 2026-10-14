import sys
from collections import deque

data = sys.stdin.read().split()
n, m = int(data[0]), int(data[1])
lanes = [[] for _ in range(n + 1)]
for i in range(m):
    a, b, w = int(data[2 + 3 * i]), int(data[3 + 3 * i]), int(data[4 + 3 * i])
    lanes[a].append((b, w))
cost = {1: 0}
queue = deque([1])
while queue:
    node = queue.popleft()
    for other, w in lanes[node]:
        if other not in cost:
            cost[other] = cost[node] + w
            queue.append(other)
print(cost.get(n, -1))
