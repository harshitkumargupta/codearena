s = input().strip()
candidates = [s] + [s[:i] + s[i + 1:] for i in range(len(s))]
print("YES" if any(c == c[::-1] for c in candidates) else "NO")
