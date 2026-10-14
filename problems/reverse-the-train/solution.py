import sys


def main():
    data = sys.stdin.buffer.read().split()
    n, head = int(data[0]), int(data[1])
    labels = [0] * (n + 1)
    behind = [0] * (n + 1)
    for i in range(1, n + 1):
        labels[i] = data[2 * i]
        behind[i] = int(data[2 * i + 1])
    order = []
    node = head
    while node:
        order.append(labels[node])
        node = behind[node]
    order.reverse()
    sys.stdout.write(b" ".join(order).decode() + "\n")


main()
