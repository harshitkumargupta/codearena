import sys


def main():
    data = sys.stdin.buffer.read().split()
    n, k = int(data[0]), int(data[1])
    counts = list(map(int, data[2:2 + n]))
    window = sum(counts[:k])
    best = window
    for i in range(k, n):
        window += counts[i] - counts[i - k]
        if window > best:
            best = window
    print(best)


main()
