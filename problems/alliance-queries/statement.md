In a strategy game there are `n` clans, each on its own at the start. Over time the game log records two kinds of
events. `U a b` means clans `a` and `b` sign a treaty, which joins their whole alliances into one. `Q a b` asks whether
clans `a` and `b` are currently in the same alliance. Answer every question in the order they appear.

### Input
The first line contains `n` and `q`. Each of the next `q` lines is an event `U a b` or `Q a b`.

### Output
For every `Q` event print `YES` or `NO` on its own line.

### Constraints
- `1 <= n <= 100000`
- `1 <= q <= 60000`
- `1 <= a, b <= n` (`a` may equal `b`)
