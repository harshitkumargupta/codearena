import sys

data = sys.stdin.read().split()
n = int(data[0])
income = [int(x) for x in data[1:1 + n]]
best = 0
for mask in range(1 << n):
    if mask & (mask >> 1):
        continue
    best = max(best, sum(income[i] for i in range(n) if mask >> i & 1))
print(best)
