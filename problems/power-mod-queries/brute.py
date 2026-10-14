import sys

data = sys.stdin.read().split()
q = int(data[0])
for i in range(q):
    a, b, m = int(data[1 + 3 * i]), int(data[2 + 3 * i]), int(data[3 + 3 * i])
    value = 1
    for _ in range(b):
        value = value * a % m
    print(value % m)
