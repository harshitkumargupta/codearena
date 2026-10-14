import sys


def main():
    data = sys.stdin.buffer.read().split()
    t = int(data[0])
    tokens = data[1:1 + t]
    floor_sums = []
    current = [int(tokens[0])]
    pos = 1
    while current:
        floor_sums.append(sum(current))
        following = []
        for _ in current:
            for _side in range(2):
                if pos < t:
                    if tokens[pos] != b"x":
                        following.append(int(tokens[pos]))
                    pos += 1
        current = following
    best = max(floor_sums)
    print(floor_sums.index(best) + 1)


main()
