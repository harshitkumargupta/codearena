import sys

MOD = 1_000_000_007


def main():
    lines = sys.stdin.read().split()
    rows, cols = int(lines[0]), int(lines[1])
    grid = lines[2:2 + rows]
    best_up = [-1] * cols
    ways_up = [0] * cols
    for r in range(rows):
        row = grid[r]
        best = [-1] * cols
        ways = [0] * cols
        for c in range(cols):
            ch = row[c]
            if ch == "#":
                continue
            if r == 0 and c == 0:
                best[0], ways[0] = int(ch), 1
                continue
            top, left = best_up[c], best[c - 1] if c else -1
            if top < 0 and left < 0:
                continue
            if top > left:
                b, w = top, ways_up[c]
            elif left > top:
                b, w = left, ways[c - 1]
            else:
                b, w = top, (ways_up[c] + ways[c - 1]) % MOD
            best[c], ways[c] = b + int(ch), w
        best_up, ways_up = best, ways
    print(f"{best_up[-1]} {ways_up[-1]}" if best_up[-1] >= 0 else "-1")


main()
