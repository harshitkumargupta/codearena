import sys
from collections import deque


def main():
    data = sys.stdin.buffer.read().split()
    n, w = int(data[0]), int(data[1])
    window = deque()
    out = []
    for i in range(n):
        t = int(data[2 + i])
        window.append(t)
        while window[0] < t - w + 1:
            window.popleft()
        out.append(len(window))
    sys.stdout.write("\n".join(map(str, out)) + "\n")


main()
