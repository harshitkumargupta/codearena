import sys

data = sys.stdin.read().split()
n, k = int(data[0]), int(data[1])
tags = data[2:2 + n]
remaining = sorted(set(tags))
for _ in range(k):
    best = None
    for tag in remaining:
        count = tags.count(tag)
        if best is None or count > best[1] or (count == best[1] and tag < best[0]):
            best = (tag, count)
    remaining.remove(best[0])
    print(best[0], best[1])
