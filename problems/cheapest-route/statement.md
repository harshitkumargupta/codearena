A courier company connects `n` depots with one-way delivery lanes. Each lane has a toll. A parcel starts at depot 1
and must reach depot `n`. Find the smallest total toll of a route from depot 1 to depot `n`.

### Input
The first line contains `n` and `m`. Each of the next `m` lines contains a lane: its start depot `a`, end depot `b`
and toll `w`. Lanes are one-way, from `a` to `b`.

### Output
Print the smallest total toll from depot 1 to depot `n`, or `-1` if depot `n` cannot be reached.

### Constraints
- `1 <= n <= 50000`
- `0 <= m <= 50000`
- `1 <= a, b <= n`
- `0 <= w <= 1000000`
