import sys


def main():
    data = sys.stdin.buffer.read().split()
    n, q = int(data[0]), int(data[1])
    keys = list(map(int, data[2:2 + n]))
    left = [-1] * n
    right = [-1] * n
    depth = {keys[0]: 0}
    for i in range(1, n):
        key, node, d = keys[i], 0, 1
        while True:
            if key < keys[node]:
                if left[node] < 0:
                    left[node] = i
                    break
                node = left[node]
            else:
                if right[node] < 0:
                    right[node] = i
                    break
                node = right[node]
            d += 1
        depth[key] = d
    queries = data[2 + n:2 + n + q]
    sys.stdout.write(" ".join(str(depth.get(int(x), -1)) for x in queries) + "\n")


main()
