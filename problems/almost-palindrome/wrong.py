s = input().strip()
lo, hi = 0, len(s) - 1
while lo < hi and s[lo] == s[hi]:
    lo += 1
    hi -= 1
rest = s[lo + 1:hi + 1]
print("YES" if lo >= hi or rest == rest[::-1] else "NO")
