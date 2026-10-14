import sys

data = sys.stdin.read().split()
n, capacity = int(data[0]), int(data[1])
snacks = sorted(((int(data[2 + 2 * i]), int(data[3 + 2 * i])) for i in range(n)),
                key=lambda s: -s[1] / s[0])
total, room = 0, capacity
for weight, energy in snacks:
    if weight <= room:
        room -= weight
        total += energy
print(total)
