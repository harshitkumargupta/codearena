import sys
from collections import deque


def main():
    lines = sys.stdin.read().split()
    rows, cols = int(lines[0]), int(lines[1])
    grid = "".join(lines[2:2 + rows])
    start, target = grid.index("S"), grid.index("T")
    dist = [-1] * (rows * cols)
    dist[start] = 0
    queue = deque([start])
    while queue:
        cell = queue.popleft()
        if cell == target:
            break
        r, c = divmod(cell, cols)
        for nr, nc in ((r - 1, c), (r + 1, c), (r, c - 1), (r, c + 1)):
            if 0 <= nr < rows and 0 <= nc < cols:
                nxt = nr * cols + nc
                if dist[nxt] < 0 and grid[nxt] != "#":
                    dist[nxt] = dist[cell] + 1
                    queue.append(nxt)
    print(dist[target])


main()
