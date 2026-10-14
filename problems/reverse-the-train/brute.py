import sys

data = sys.stdin.read().split()
n, head = int(data[0]), int(data[1])
labels = {}
behind = {}
for i in range(1, n + 1):
    labels[i] = data[2 * i]
    behind[i] = int(data[2 * i + 1])
current = next(i for i in behind if behind[i] == 0)
result = [labels[current]]
while current != head:
    current = next(i for i in behind if behind[i] == current)
    result.append(labels[current])
print(" ".join(result))
