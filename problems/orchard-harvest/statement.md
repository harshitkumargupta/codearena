An orchard is laid out as a grid of `R` rows and `C` columns. Each cell is either a tree with `0` to `9` ripe apples,
or a pond `#` that cannot be entered. A picker starts in the top-left cell and must reach the bottom-right cell,
moving only one cell right or one cell down at a time, and collects the apples of every cell visited, the first and
last included. Find the largest number of apples a route can collect, and how many different routes collect exactly
that many. The number of routes can be huge, so print it modulo `1000000007`.

### Input
The first line contains `R` and `C`. Each of the next `R` lines contains `C` characters, each a digit or `#`.

### Output
Print the largest harvest and the number of routes achieving it modulo `1000000007`, separated by a space. If no route
exists (including when the start or the end is a pond), print `-1`.

### Constraints
- `1 <= R, C <= 600`
