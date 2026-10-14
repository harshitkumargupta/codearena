import sys
from collections import deque

lines = sys.stdin.read().split()
rows, cols = int(lines[0]), int(lines[1])
grid = lines[2:2 + rows]
start = next((r, c) for r in range(rows) for c in range(cols) if grid[r][c] == "S")
dist = {start: 0}
queue = deque([start])
while queue:
    r, c = queue.popleft()
    for dr in (-1, 0, 1):
        for dc in (-1, 0, 1):
            nr, nc = r + dr, c + dc
            if 0 <= nr < rows and 0 <= nc < cols and grid[nr][nc] != "#" and (nr, nc) not in dist:
                dist[(nr, nc)] = dist[(r, c)] + 1
                queue.append((nr, nc))
target = next((r, c) for r in range(rows) for c in range(cols) if grid[r][c] == "T")
print(dist.get(target, -1))
