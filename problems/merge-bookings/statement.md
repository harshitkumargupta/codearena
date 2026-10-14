A studio rents out its recording room. Each booking occupies the room from minute `s` up to, but not including,
minute `e`. For the cleaning schedule the manager wants the room's busy blocks: bookings that overlap, or where one
ends exactly when another begins, belong to the same block. Print the blocks in order of start time.

### Input
The first line contains `n`. Each of the next `n` lines contains a booking's `s` and `e`. Bookings are in no
particular order and may repeat.

### Output
On the first line print the number of blocks `b`. Then print `b` lines, each with the start and end minute of a
block, in increasing order of start.

### Constraints
- `1 <= n <= 40000`
- `0 <= s < e <= 1000000000`
