import sys

data = sys.stdin.read().split()
n, limit = int(data[0]), int(data[1])
weights = [int(x) for x in data[2:2 + n]]
trips, i = 0, 0
while i < n:
    if i + 1 < n and weights[i] + weights[i + 1] <= limit:
        i += 2
    else:
        i += 1
    trips += 1
print(trips)
