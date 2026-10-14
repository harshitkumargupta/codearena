A photo-sharing app wants a "trending" panel. Given the tags used in today's posts, show the `k` tags used most
often. When two tags were used equally often, the one that comes first alphabetically ranks higher.

### Input
The first line contains `n` and `k`. The second line contains `n` tags separated by spaces; each tag is a word of
lowercase English letters.

### Output
Print `k` lines, from the highest ranked tag down: the tag and how many times it was used.

### Constraints
- `1 <= n <= 80000`
- `1 <= length of a tag <= 8`
- `1 <= k <=` the number of distinct tags
