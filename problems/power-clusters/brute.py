import sys

data = sys.stdin.read().split()
n, m = int(data[0]), int(data[1])
label = list(range(n + 1))
for i in range(m):
    a, b = int(data[2 + 2 * i]), int(data[3 + 2 * i])
    old, new = label[a], label[b]
    if old != new:
        for node in range(1, n + 1):
            if label[node] == old:
                label[node] = new
sizes = {}
for node in range(1, n + 1):
    sizes[label[node]] = sizes.get(label[node], 0) + 1
print(len(sizes), max(sizes.values()))
