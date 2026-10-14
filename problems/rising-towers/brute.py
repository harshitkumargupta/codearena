import sys

data = sys.stdin.read().split()
n = int(data[0])
heights = [int(x) for x in data[1:1 + n]]
best = [1] * n
for i in range(n):
    for j in range(i):
        if heights[j] < heights[i] and best[j] + 1 > best[i]:
            best[i] = best[j] + 1
print(max(best))
