import sys

data = sys.stdin.read().split()
n = int(data[0])
income = [int(x) for x in data[1:1 + n]]
print(max(sum(income[0::2]), sum(income[1::2])))
