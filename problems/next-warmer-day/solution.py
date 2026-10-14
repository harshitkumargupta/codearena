import sys


def main():
    data = sys.stdin.buffer.read().split()
    n = int(data[0])
    temps = list(map(int, data[1:1 + n]))
    wait = [0] * n
    stack = []
    for i, t in enumerate(temps):
        while stack and temps[stack[-1]] < t:
            j = stack.pop()
            wait[j] = i - j
        stack.append(i)
    sys.stdout.write(" ".join(map(str, wait)) + "\n")


main()
