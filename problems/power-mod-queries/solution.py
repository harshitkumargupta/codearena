import sys


def main():
    data = sys.stdin.buffer.read().split()
    q = int(data[0])
    out = []
    for i in range(q):
        a, b, m = int(data[1 + 3 * i]), int(data[2 + 3 * i]), int(data[3 + 3 * i])
        out.append(pow(a, b, m))
    sys.stdout.write("\n".join(map(str, out)) + "\n")


main()
