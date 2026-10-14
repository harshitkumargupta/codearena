import sys

data = sys.stdin.read().split()
n, capacity = int(data[0]), int(data[1])
snacks = [(int(data[2 + 2 * i]), int(data[3 + 2 * i])) for i in range(n)]
best = 0
for mask in range(1 << n):
    weight = sum(snacks[i][0] for i in range(n) if mask >> i & 1)
    if weight <= capacity:
        best = max(best, sum(snacks[i][1] for i in range(n) if mask >> i & 1))
print(best)
