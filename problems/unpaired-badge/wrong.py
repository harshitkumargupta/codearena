import sys

data = sys.stdin.read().split()
n = int(data[0])
ids = sorted(map(int, data[1:1 + n]))
print(ids[n // 2])
