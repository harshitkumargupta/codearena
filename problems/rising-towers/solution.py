import sys
from bisect import bisect_left


def main():
    data = sys.stdin.buffer.read().split()
    n = int(data[0])
    tails = []
    for token in data[1:1 + n]:
        h = int(token)
        i = bisect_left(tails, h)
        if i == len(tails):
            tails.append(h)
        else:
            tails[i] = h
    print(len(tails))


main()
