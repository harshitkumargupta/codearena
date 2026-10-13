INSERT INTO problems (slug, title, statement_md, difficulty, time_limit_ms, memory_limit_mb, published) VALUES
    ('two-sum', 'Two Sum', $md$Given an array of `n` integers and a target, print the 0-based indices `i < j` of the two numbers that add up to the target.

Exactly one valid pair exists.

### Input
The first line contains `n` and `target`. The second line contains `n` space-separated integers.

### Output
Print `i` and `j` separated by a space.

### Constraints
- `2 <= n <= 10^5`
- `-10^9 <= a[i], target <= 10^9`$md$, 'EASY', 2000, 256, TRUE),
    ('fizzbuzz', 'FizzBuzz', $md$Print the numbers from `1` to `n`, one per line. For multiples of 3 print `Fizz`, for multiples of 5 print `Buzz`, and for multiples of both print `FizzBuzz`.

### Input
A single integer `n`.

### Output
`n` lines as described.

### Constraints
- `1 <= n <= 10^4`$md$, 'EASY', 2000, 256, TRUE),
    ('valid-parentheses', 'Valid Parentheses', $md$Given a string containing only the characters `()[]{}`, decide whether it is valid. A string is valid if every opening bracket is closed by the same type of bracket, in the correct order.

### Input
A single line containing the string `s`.

### Output
Print `true` if `s` is valid, otherwise `false`.

### Constraints
- `1 <= |s| <= 10^5`$md$, 'EASY', 2000, 256, TRUE),
    ('reverse-string', 'Reverse String', $md$Print the given line of text reversed.

### Input
A single line `s`, which may contain spaces.

### Output
The characters of `s` in reverse order.

### Constraints
- `1 <= |s| <= 10^5`
- `s` contains printable ASCII characters$md$, 'EASY', 2000, 256, TRUE),
    ('fibonacci', 'Fibonacci Number', $md$The Fibonacci numbers are defined as `F(0) = 0`, `F(1) = 1`, and `F(n) = F(n-1) + F(n-2)` for `n > 1`. Print `F(n)`.

### Input
A single integer `n`.

### Output
The value of `F(n)`.

### Constraints
- `0 <= n <= 90` (the answer fits in a signed 64-bit integer)$md$, 'EASY', 2000, 256, TRUE),
    ('maximum-subarray', 'Maximum Subarray', $md$Given an array of `n` integers, find the contiguous non-empty subarray with the largest sum and print that sum.

### Input
The first line contains `n`. The second line contains `n` space-separated integers.

### Output
The largest subarray sum.

### Constraints
- `1 <= n <= 10^5`
- `-10^4 <= a[i] <= 10^4`$md$, 'MEDIUM', 2000, 256, TRUE);

INSERT INTO test_cases (problem_id, input, expected_output, is_sample, ordinal)
SELECT p.id, v.input, v.expected_output, v.is_sample, v.ordinal
FROM problems p, (VALUES
    (E'4 9\n2 7 11 15\n', E'0 1', TRUE, 1),
    (E'3 6\n3 2 4\n', E'1 2', TRUE, 2),
    (E'2 6\n3 3\n', E'0 1', FALSE, 3),
    (E'5 -8\n-1 -2 -3 -4 -5\n', E'2 4', FALSE, 4),
    (E'6 0\n0 4 -4 9 10 11\n', E'1 2', FALSE, 5),
    (E'6 1000000000\n999999999 5 -7 1 3 8\n', E'0 3', FALSE, 6)
) AS v(input, expected_output, is_sample, ordinal)
WHERE p.slug = 'two-sum';

INSERT INTO test_cases (problem_id, input, expected_output, is_sample, ordinal)
SELECT p.id, v.input, v.expected_output, v.is_sample, v.ordinal
FROM problems p, (VALUES
    (E'3\n', E'1\n2\nFizz', TRUE, 1),
    (E'5\n', E'1\n2\nFizz\n4\nBuzz', TRUE, 2),
    (E'1\n', E'1', FALSE, 3),
    (E'15\n', E'1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz', FALSE, 4),
    (E'100\n', E'1\n2\nFizz\n4\nBuzz\nFizz\n7\n8\nFizz\nBuzz\n11\nFizz\n13\n14\nFizzBuzz\n16\n17\nFizz\n19\nBuzz\nFizz\n22\n23\nFizz\nBuzz\n26\nFizz\n28\n29\nFizzBuzz\n31\n32\nFizz\n34\nBuzz\nFizz\n37\n38\nFizz\nBuzz\n41\nFizz\n43\n44\nFizzBuzz\n46\n47\nFizz\n49\nBuzz\nFizz\n52\n53\nFizz\nBuzz\n56\nFizz\n58\n59\nFizzBuzz\n61\n62\nFizz\n64\nBuzz\nFizz\n67\n68\nFizz\nBuzz\n71\nFizz\n73\n74\nFizzBuzz\n76\n77\nFizz\n79\nBuzz\nFizz\n82\n83\nFizz\nBuzz\n86\nFizz\n88\n89\nFizzBuzz\n91\n92\nFizz\n94\nBuzz\nFizz\n97\n98\nFizz\nBuzz', FALSE, 5)
) AS v(input, expected_output, is_sample, ordinal)
WHERE p.slug = 'fizzbuzz';

INSERT INTO test_cases (problem_id, input, expected_output, is_sample, ordinal)
SELECT p.id, v.input, v.expected_output, v.is_sample, v.ordinal
FROM problems p, (VALUES
    (E'()\n', E'true', TRUE, 1),
    (E'()[]{}\n', E'true', TRUE, 2),
    (E'(]\n', E'false', TRUE, 3),
    (E'([)]\n', E'false', FALSE, 4),
    (E'{[]}\n', E'true', FALSE, 5),
    (E'((\n', E'false', FALSE, 6),
    (E']\n', E'false', FALSE, 7),
    (E'{[()()]}\n', E'true', FALSE, 8),
    (E'(((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((((())))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))))\n', E'true', FALSE, 9)
) AS v(input, expected_output, is_sample, ordinal)
WHERE p.slug = 'valid-parentheses';

INSERT INTO test_cases (problem_id, input, expected_output, is_sample, ordinal)
SELECT p.id, v.input, v.expected_output, v.is_sample, v.ordinal
FROM problems p, (VALUES
    (E'hello\n', E'olleh', TRUE, 1),
    (E'Hannah\n', E'hannaH', TRUE, 2),
    (E'a\n', E'a', FALSE, 3),
    (E'abc def\n', E'fed cba', FALSE, 4),
    (E'racecar\n', E'racecar', FALSE, 5),
    (E'CodeArena 2026!\n', E'!6202 anerAedoC', FALSE, 6)
) AS v(input, expected_output, is_sample, ordinal)
WHERE p.slug = 'reverse-string';

INSERT INTO test_cases (problem_id, input, expected_output, is_sample, ordinal)
SELECT p.id, v.input, v.expected_output, v.is_sample, v.ordinal
FROM problems p, (VALUES
    (E'2\n', E'1', TRUE, 1),
    (E'10\n', E'55', TRUE, 2),
    (E'0\n', E'0', FALSE, 3),
    (E'1\n', E'1', FALSE, 4),
    (E'50\n', E'12586269025', FALSE, 5),
    (E'90\n', E'2880067194370816120', FALSE, 6)
) AS v(input, expected_output, is_sample, ordinal)
WHERE p.slug = 'fibonacci';

INSERT INTO test_cases (problem_id, input, expected_output, is_sample, ordinal)
SELECT p.id, v.input, v.expected_output, v.is_sample, v.ordinal
FROM problems p, (VALUES
    (E'9\n-2 1 -3 4 -1 2 1 -5 4\n', E'6', TRUE, 1),
    (E'1\n1\n', E'1', TRUE, 2),
    (E'5\n5 4 -1 7 8\n', E'23', FALSE, 3),
    (E'3\n-3 -1 -2\n', E'-1', FALSE, 4),
    (E'4\n-1 2 3 -10\n', E'5', FALSE, 5),
    (E'6\n2 -1 2 -1 2 -1\n', E'4', FALSE, 6)
) AS v(input, expected_output, is_sample, ordinal)
WHERE p.slug = 'maximum-subarray';
