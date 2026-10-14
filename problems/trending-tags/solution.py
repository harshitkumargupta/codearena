import heapq
import sys
from collections import Counter


def main():
    data = sys.stdin.buffer.read().split()
    n, k = int(data[0]), int(data[1])
    counts = Counter(data[2:2 + n])
    top = heapq.nsmallest(k, counts.items(), key=lambda item: (-item[1], item[0]))
    sys.stdout.write("".join(f"{tag.decode()} {count}\n" for tag, count in top))


main()
