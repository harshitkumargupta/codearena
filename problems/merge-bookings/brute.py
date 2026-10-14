import sys

data = sys.stdin.read().split()
n = int(data[0])
busy = set()
for i in range(n):
    s, e = int(data[1 + 2 * i]), int(data[2 + 2 * i])
    for minute in range(s, e):
        busy.add(minute)
blocks = []
for minute in sorted(busy):
    if blocks and blocks[-1][1] == minute:
        blocks[-1][1] = minute + 1
    else:
        blocks.append([minute, minute + 1])
print(len(blocks))
for s, e in blocks:
    print(s, e)
