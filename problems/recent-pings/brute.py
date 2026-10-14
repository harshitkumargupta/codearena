import sys

data = sys.stdin.read().split()
n, w = int(data[0]), int(data[1])
times = [int(x) for x in data[2:2 + n]]
for i in range(n):
    count = 0
    for j in range(i + 1):
        if times[j] >= times[i] - w + 1:
            count += 1
    print(count)
