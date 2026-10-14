import sys

data = sys.stdin.read().split()
n = int(data[0])
bookings = sorted((int(data[1 + 2 * i]), int(data[2 + 2 * i])) for i in range(n))
blocks = []
start, end = bookings[0]
for s, e in bookings[1:]:
    if s < end:
        end = max(end, e)
    else:
        blocks.append((start, end))
        start, end = s, e
blocks.append((start, end))
print(len(blocks))
print("\n".join(f"{s} {e}" for s, e in blocks))
