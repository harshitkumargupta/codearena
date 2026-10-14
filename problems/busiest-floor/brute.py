import sys

data = sys.stdin.read().split()
t = int(data[0])
tokens = data[1:1 + t]


class Node:
    def __init__(self, value):
        self.value = value
        self.left = None
        self.right = None


root = Node(int(tokens[0]))
queue = [root]
pos = 1
head = 0
while head < len(queue) and pos < t:
    node = queue[head]
    head += 1
    for side in ("left", "right"):
        if pos < t:
            if tokens[pos] != "x":
                child = Node(int(tokens[pos]))
                setattr(node, side, child)
                queue.append(child)
            pos += 1

sums = {}


def visit(node, depth):
    if node is None:
        return
    sums[depth] = sums.get(depth, 0) + node.value
    visit(node.left, depth + 1)
    visit(node.right, depth + 1)


visit(root, 1)
best = max(sums.values())
print(min(d for d, s in sums.items() if s == best))
