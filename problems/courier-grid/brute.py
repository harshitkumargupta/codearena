import sys

lines = sys.stdin.read().split()
rows, cols = int(lines[0]), int(lines[1])
grid = lines[2:2 + rows]
INF = float("inf")
dist = {}
for r in range(rows):
    for c in range(cols):
        if grid[r][c] != "#":
            dist[(r, c)] = 0 if grid[r][c] == "S" else INF
changed = True
while changed:
    changed = False
    for (r, c) in dist:
        for other in ((r - 1, c), (r + 1, c), (r, c - 1), (r, c + 1)):
            if other in dist and dist[other] + 1 < dist[(r, c)]:
                dist[(r, c)] = dist[other] + 1
                changed = True
target = next((r, c) for r in range(rows) for c in range(cols) if grid[r][c] == "T")
print(-1 if dist[target] == INF else dist[target])
