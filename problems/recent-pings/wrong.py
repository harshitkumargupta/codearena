import sys
from collections import deque

data = sys.stdin.read().split()
n, w = int(data[0]), int(data[1])
window = deque()
out = []
for i in range(n):
    t = int(data[2 + i])
    window.append(t)
    while window[0] < t - w:
        window.popleft()
    out.append(len(window))
print("\n".join(map(str, out)))
