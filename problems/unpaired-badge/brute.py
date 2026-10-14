import sys

data = sys.stdin.read().split()
n = int(data[0])
ids = data[1:1 + n]
for badge in ids:
    if ids.count(badge) == 1:
        print(badge)
        break
