import sys


def main():
    data = sys.stdin.buffer.read().split()
    n, capacity = int(data[0]), int(data[1])
    best = [0] * (capacity + 1)
    for i in range(n):
        weight, energy = int(data[2 + 2 * i]), int(data[3 + 2 * i])
        if weight > capacity:
            continue
        shifted = [0] * weight + [b + energy for b in best[:capacity + 1 - weight]]
        best = [max(a, b) for a, b in zip(best, shifted)]
    print(best[capacity])


main()
