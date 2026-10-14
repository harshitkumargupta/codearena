import sys


def main():
    data = sys.stdin.buffer.read().split()
    n, hours = int(data[0]), int(data[1])
    volumes = list(map(int, data[2:2 + n]))
    less_one = [v - 1 for v in volumes]
    lo, hi = max(1, -(-sum(volumes) // hours)), max(volumes)
    while lo < hi:
        rate = (lo + hi) // 2
        if n + sum(map(rate.__rfloordiv__, less_one)) <= hours:
            hi = rate
        else:
            lo = rate + 1
    print(lo)


main()
