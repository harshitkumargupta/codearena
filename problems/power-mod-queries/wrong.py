import sys

data = sys.stdin.read().split()
q = int(data[0])
out = []
for i in range(q):
    a, b, m = int(data[1 + 3 * i]), int(data[2 + 3 * i]), int(data[3 + 3 * i])
    out.append(pow(a, b, m) if b > 0 else 1)
print("\n".join(map(str, out)))
