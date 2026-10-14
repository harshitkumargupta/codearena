import sys


def main():
    data = sys.stdin.buffer.read().split()
    n, m = int(data[0]), int(data[1])
    neighbours = [[] for _ in range(n + 1)]
    for i in range(m):
        a, b = int(data[2 + 2 * i]), int(data[3 + 2 * i])
        neighbours[a].append(b)
        neighbours[b].append(a)
    seen = [False] * (n + 1)
    clusters, largest = 0, 0
    for start in range(1, n + 1):
        if seen[start]:
            continue
        clusters += 1
        seen[start] = True
        stack, size = [start], 0
        while stack:
            node = stack.pop()
            size += 1
            for other in neighbours[node]:
                if not seen[other]:
                    seen[other] = True
                    stack.append(other)
        largest = max(largest, size)
    print(clusters, largest)


main()
