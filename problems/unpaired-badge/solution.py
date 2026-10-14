import sys


def main():
    data = sys.stdin.buffer.read().split()
    n = int(data[0])
    result = 0
    for token in data[1:1 + n]:
        result ^= int(token)
    print(result)


main()
