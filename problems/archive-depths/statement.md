A records office files folders into a binary search tree. Folder numbers are inserted one by one, in the given
order, into an initially empty tree: a new number walks down from the root, going left when it is smaller than the
current folder and right when it is larger, until it finds an empty place. The first folder becomes the root at depth
0. Afterwards clerks ask at what depth a folder sits. Answer each question with the depth, or `-1` if the folder was
never filed.

### Input
The first line contains `n` and `q`. The second line contains the `n` distinct folder numbers in insertion order. The
third line contains the `q` folder numbers asked about.

### Output
Print the `q` answers separated by spaces.

### Constraints
- `1 <= n, q <= 2000`
- `1 <= folder number <= 1000000`
- The inserted numbers are distinct.
