A monitoring service receives health pings from a server and writes down the time of each one, in milliseconds. The
times are listed in the order the pings arrived, so they never decrease. After each ping, the dashboard shows how
many pings arrived during the last `W` milliseconds: the pings received so far (this one included) whose time is at
least `t - W + 1`, where `t` is the time of the current ping. Print what the dashboard shows after every ping.

### Input
The first line contains `n` and `W`. The second line contains the `n` ping times in arrival order.

### Output
Print `n` lines; line `i` is the count shown after ping `i`.

### Constraints
- `1 <= n <= 80000`
- `1 <= W <= 100000000`
- `0 <= time <= 100000000`, and the times never decrease
