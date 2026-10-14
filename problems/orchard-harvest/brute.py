import sys

lines = sys.stdin.read().split()
rows, cols = int(lines[0]), int(lines[1])
grid = lines[2:2 + rows]
totals = []


def walk(r, c, total):
    if grid[r][c] == "#":
        return
    total += int(grid[r][c])
    if r == rows - 1 and c == cols - 1:
        totals.append(total)
        return
    if r + 1 < rows:
        walk(r + 1, c, total)
    if c + 1 < cols:
        walk(r, c + 1, total)


walk(0, 0, 0)
if totals:
    best = max(totals)
    print(best, totals.count(best) % 1000000007)
else:
    print(-1)
