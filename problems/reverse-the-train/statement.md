A toy train has `n` carriages, numbered 1 to `n` in the order they were built, not in the order they are coupled.
Each carriage carries a label and knows which carriage is coupled behind it. Starting from the engine's carriage
`head` and following the couplings visits every carriage exactly once. The train is about to be turned around:
print the labels in the order they will appear after reversing, that is, from the last carriage back to the first.

### Input
The first line contains `n` and `head`. Line `i + 1` (for `i` from 1 to `n`) describes carriage `i`: its label and
the number of the carriage behind it, or `0` if it is the last one.

### Output
Print the `n` labels from the last carriage to the first, separated by spaces.

### Constraints
- `1 <= n <= 60000`
- `1 <= head <= n`
- `1 <= label <= 1000000`
- The couplings form a single chain from `head` through all `n` carriages.
