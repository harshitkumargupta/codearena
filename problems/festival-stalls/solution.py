import sys


def main():
    data = sys.stdin.buffer.read().split()
    n = int(data[0])
    skip, take = 0, 0
    for token in data[1:1 + n]:
        skip, take = max(skip, take), skip + int(token)
    print(max(skip, take))


main()
