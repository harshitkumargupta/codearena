import sys

data = sys.stdin.read().split()
n, q = int(data[0]), int(data[1])
keys = [int(x) for x in data[2:2 + n]]
tree = {}


def insert(node, key):
    if node is None:
        return {"key": key, "left": None, "right": None}
    side = "left" if key < node["key"] else "right"
    node[side] = insert(node[side], key)
    return node


root = None
for key in keys:
    root = insert(root, key)
answers = []
for x in data[2 + n:2 + n + q]:
    target, node, d = int(x), root, 0
    while node is not None and node["key"] != target:
        node = node["left"] if target < node["key"] else node["right"]
        d += 1
    answers.append(str(d if node is not None else -1))
print(" ".join(answers))
