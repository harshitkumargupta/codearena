import sys

data = sys.stdin.read().split()
n, k = int(data[0]), int(data[1])
counts = [int(x) for x in data[2:2 + n]]
size = max(1, k - 1)
window = sum(counts[:size])
best = window
for i in range(size, n):
    window += counts[i] - counts[i - size]
    best = max(best, window)
print(best)
