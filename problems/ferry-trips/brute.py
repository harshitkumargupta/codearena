import sys
from functools import lru_cache

data = sys.stdin.read().split()
n, limit = int(data[0]), int(data[1])
weights = [int(x) for x in data[2:2 + n]]


@lru_cache(maxsize=None)
def best(left):
    if left == 0:
        return 0
    first = (left & -left).bit_length() - 1
    rest = left & ~(1 << first)
    answer = 1 + best(rest)
    for other in range(n):
        if rest >> other & 1 and weights[first] + weights[other] <= limit:
            answer = min(answer, 1 + best(rest & ~(1 << other)))
    return answer


print(best((1 << n) - 1))
