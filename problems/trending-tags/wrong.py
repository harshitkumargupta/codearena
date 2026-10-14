import sys

data = sys.stdin.read().split()
n, k = int(data[0]), int(data[1])
counts = {}
for tag in data[2:2 + n]:
    counts[tag] = counts.get(tag, 0) + 1
ranked = sorted(counts.items(), key=lambda item: -item[1])
print("\n".join(f"{tag} {count}" for tag, count in ranked[:k]))
