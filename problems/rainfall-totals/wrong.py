import sys

data = sys.stdin.read().split()
n, q = int(data[0]), int(data[1])
prefix = [0]
for x in data[2:2 + n]:
    prefix.append(prefix[-1] + int(x))
pos = 2 + n
out = []
for _ in range(q):
    l, r = int(data[pos]), int(data[pos + 1])
    pos += 2
    out.append(prefix[r] - prefix[l])
print("\n".join(map(str, out)))
