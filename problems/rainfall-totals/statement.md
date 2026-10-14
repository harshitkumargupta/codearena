A weather station on a hillside writes down how many millimetres of rain fell on each of `n` consecutive days. The
farmers nearby keep asking the same kind of question: how much rain fell in total from day `l` to day `r`, both
days included? Answer all `q` of their questions.

Days are numbered from 1.

### Input
The first line contains `n` and `q`. The second line contains `n` integers, the rainfall of days 1 to `n`. Each of the
next `q` lines contains two integers `l` and `r`.

### Output
For each question, print the total rainfall from day `l` to day `r` on its own line, in the order of the questions.

### Constraints
- `1 <= n, q <= 40000`
- `0 <= rainfall of a day <= 10000`
- `1 <= l <= r <= n`
