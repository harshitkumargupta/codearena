import sys


def reads_both_ways(s, lo, hi):
    while lo < hi:
        if s[lo] != s[hi]:
            return False
        lo += 1
        hi -= 1
    return True


def main():
    s = sys.stdin.readline().strip()
    lo, hi = 0, len(s) - 1
    while lo < hi and s[lo] == s[hi]:
        lo += 1
        hi -= 1
    ok = lo >= hi or reads_both_ways(s, lo + 1, hi) or reads_both_ways(s, lo, hi - 1)
    print("YES" if ok else "NO")


main()
