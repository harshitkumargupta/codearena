import sys


def main():
    data = sys.stdin.read().split()
    n, m = int(data[0]), int(data[1])
    full = (1 << n) - 1
    allowed = [full] * (n + 1)
    for i in range(m):
        a, b = int(data[2 + 2 * i]) - 1, int(data[3 + 2 * i]) - 1
        allowed[a] &= ~(1 << b)
        allowed[b] &= ~(1 << a)

    def place(free, last):
        if not free:
            return 1
        total = 0
        options = free & allowed[last]
        while options:
            bit = options & -options
            options ^= bit
            total += place(free ^ bit, bit.bit_length() - 1)
        return total

    print(place(full, n))


main()
