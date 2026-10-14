import sys


def main():
    data = sys.stdin.buffer.read().split()
    n, limit = int(data[0]), int(data[1])
    weights = sorted(map(int, data[2:2 + n]))
    light, heavy, trips = 0, n - 1, 0
    while light <= heavy:
        if light < heavy and weights[light] + weights[heavy] <= limit:
            light += 1
        heavy -= 1
        trips += 1
    print(trips)


main()
