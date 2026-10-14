A wedding planner must seat `n` guests, numbered 1 to `n`, on a single bench with `n` places in a row. Some pairs of
guests have fallen out and refuse to sit directly next to each other. Count the seating orders in which no such pair
sits side by side. Two orders are different if some place holds a different guest.

### Input
The first line contains `n` and `m`. Each of the next `m` lines contains two different guests `a` and `b` who refuse to
sit next to each other. No pair is listed twice.

### Output
Print the number of valid seating orders.

### Constraints
- `1 <= n <= 9`
- `0 <= m <= n * (n - 1) / 2`
- `1 <= a, b <= n`, `a != b`
