An office tower's departments are organised as a binary tree: each department has at most a left and a right
sub-department. Every department reports a profit, which may be negative. Departments at the same depth form a
floor; the top department is on floor 1, its sub-departments on floor 2, and so on. Find the floor whose departments
add up to the largest total profit. If several floors tie, print the lowest floor number.

The tree is given in level order: the root first, then for each listed department its left and right child, where
`x` means there is no child. Children of a missing department are not listed, and trailing `x` tokens are omitted.

### Input
The first line contains `t`, the number of tokens. The second line contains the `t` tokens: profits and `x` markers.

### Output
Print the floor number with the largest total profit.

### Constraints
- `1 <= number of departments <= 60000`
- `t <= 120001`
- `-10000 <= profit <= 10000`
- The first token is a profit (the tree is not empty).
