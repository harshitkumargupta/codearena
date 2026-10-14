import sys

data = sys.stdin.read().split()
n, q = int(data[0]), int(data[1])
rain = [int(x) for x in data[2:2 + n]]
pos = 2 + n
for _ in range(q):
    l, r = int(data[pos]), int(data[pos + 1])
    pos += 2
    total = 0
    for day in range(l, r + 1):
        total += rain[day - 1]
    print(total)
