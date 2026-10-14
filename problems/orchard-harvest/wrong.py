import sys

lines = sys.stdin.read().split()
rows, cols = int(lines[0]), int(lines[1])
grid = lines[2:2 + rows]
best = [[-1] * cols for _ in range(rows)]
ways = [[0] * cols for _ in range(rows)]
for r in range(rows):
    for c in range(cols):
        if grid[r][c] == "#":
            continue
        if r == 0 and c == 0:
            best[0][0], ways[0][0] = int(grid[0][0]), 1
            continue
        top = best[r - 1][c] if r else -1
        left = best[r][c - 1] if c else -1
        if max(top, left) < 0:
            continue
        if top >= left:
            best[r][c], ways[r][c] = top + int(grid[r][c]), ways[r - 1][c]
        else:
            best[r][c], ways[r][c] = left + int(grid[r][c]), ways[r][c - 1]
print(f"{best[-1][-1]} {ways[-1][-1]}" if best[-1][-1] >= 0 else "-1")
