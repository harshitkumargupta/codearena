import sys

data = sys.stdin.read().split()
n = int(data[0])
temps = [int(x) for x in data[1:1 + n]]
wait = [0] * n
stack = []
for i, t in enumerate(temps):
    while stack and temps[stack[-1]] <= t:
        j = stack.pop()
        wait[j] = i - j
    stack.append(i)
print(" ".join(map(str, wait)))
