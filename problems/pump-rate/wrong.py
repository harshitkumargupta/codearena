import sys

data = sys.stdin.read().split()
n, hours = int(data[0]), int(data[1])
volumes = [int(x) for x in data[2:2 + n]]
lo, hi = 1, max(volumes)
while lo < hi:
    rate = (lo + hi) // 2
    if sum(v // rate for v in volumes) <= hours:
        hi = rate
    else:
        lo = rate + 1
print(lo)
