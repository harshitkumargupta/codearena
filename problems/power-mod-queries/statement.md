A cryptography class is practising with huge powers. For each exercise you are given a base `a`, an exponent `b` and
a modulus `m`, and must report the remainder of `a` raised to the power `b` when divided by `m`. The exponents are far
too large to multiply out, so the students need a faster method. By convention, any number to the power 0 is 1.

### Input
The first line contains `q`. Each of the next `q` lines contains `a`, `b` and `m`.

### Output
For each exercise print `a^b mod m` on its own line.

### Constraints
- `1 <= q <= 15000`
- `0 <= a <= 10^18`
- `0 <= b <= 10^18`
- `1 <= m <= 10^9`
