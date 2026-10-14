A delivery robot works in a warehouse drawn as a grid of `R` rows and `C` columns. Each cell is open floor `.`, a
shelf `#`, the robot's start `S` or the parcel `T`. In one step the robot moves to the open cell above, below, left
or right of it; it cannot enter shelves or leave the grid. What is the smallest number of steps to reach the parcel?

### Input
The first line contains `R` and `C`. Each of the next `R` lines contains `C` characters. `S` and `T` each appear
exactly once.

### Output
Print the smallest number of steps from `S` to `T`, or `-1` if the parcel cannot be reached.

### Constraints
- `1 <= R, C <= 500`
- `R * C >= 2`
