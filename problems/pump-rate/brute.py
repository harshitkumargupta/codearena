import sys

data = sys.stdin.read().split()
n, hours = int(data[0]), int(data[1])
volumes = [int(x) for x in data[2:2 + n]]
rate = 1
while sum(-(-v // rate) for v in volumes) > hours:
    rate += 1
print(rate)
