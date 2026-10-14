import sys

data = sys.stdin.read().split()
n = int(data[0])
temps = [int(x) for x in data[1:1 + n]]
wait = []
for i in range(n):
    answer = 0
    for j in range(i + 1, n):
        if temps[j] > temps[i]:
            answer = j - i
            break
    wait.append(answer)
print(" ".join(map(str, wait)))
