import sys

data = sys.stdin.read().split()
n, m = int(data[0]), int(data[1])
rules = [(int(data[2 + 2 * i]), int(data[3 + 2 * i])) for i in range(m)]
done = set()
order = []
while len(order) < n:
    choice = None
    for task in range(1, n + 1):
        if task not in done and all(a in done for a, b in rules if b == task):
            choice = task
            break
    if choice is None:
        break
    done.add(choice)
    order.append(choice)
print(" ".join(map(str, order)) if len(order) == n else "IMPOSSIBLE")
