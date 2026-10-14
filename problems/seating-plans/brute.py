import sys
from itertools import permutations

data = sys.stdin.read().split()
n, m = int(data[0]), int(data[1])
pairs = set()
for i in range(m):
    a, b = int(data[2 + 2 * i]), int(data[3 + 2 * i])
    pairs.add((a, b))
    pairs.add((b, a))
print(sum(1 for order in permutations(range(1, n + 1))
          if all((order[i], order[i + 1]) not in pairs for i in range(n - 1))))
