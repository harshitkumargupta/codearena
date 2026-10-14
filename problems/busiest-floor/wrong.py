import sys

data = sys.stdin.read().split()
t = int(data[0])
tokens = data[1:1 + t]
sums = []
current = [int(tokens[0])]
pos = 1
while current:
    sums.append(sum(current))
    following = []
    for _ in current:
        for _side in range(2):
            if pos < t:
                if tokens[pos] != "x":
                    following.append(int(tokens[pos]))
                pos += 1
    current = following
best = max(sums)
print(len(sums) - sums[::-1].index(best))
