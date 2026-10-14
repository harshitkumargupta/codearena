import sys

data = sys.stdin.read().split()
n, k = int(data[0]), int(data[1])
counts = [int(x) for x in data[2:2 + n]]
best = None
for start in range(n - k + 1):
    total = 0
    for i in range(start, start + k):
        total += counts[i]
    if best is None or total > best:
        best = total
print(best)
