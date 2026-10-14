// Solutions for seeded problems. `expected` is the verdict the judge must return; the analysis compares them.
export const SOLUTIONS = [
  {
    key: 'two-sum-java-ok',
    slug: 'two-sum',
    language: 'JAVA',
    expected: 'ACCEPTED',
    source: `import java.util.*;

public class Main {
    public static void main(String[] args) {
        Scanner in = new Scanner(System.in);
        int n = in.nextInt();
        long target = in.nextLong();
        Map<Long, Integer> seen = new HashMap<>();
        for (int i = 0; i < n; i++) {
            long x = in.nextLong();
            Integer j = seen.get(target - x);
            if (j != null) {
                System.out.println(j + " " + i);
                return;
            }
            seen.put(x, i);
        }
    }
}
`,
  },
  {
    key: 'two-sum-python-ok',
    slug: 'two-sum',
    language: 'PYTHON',
    expected: 'ACCEPTED',
    source: `import sys
data = sys.stdin.read().split()
n, target = int(data[0]), int(data[1])
seen = {}
for i in range(n):
    x = int(data[2 + i])
    if target - x in seen:
        print(seen[target - x], i)
        break
    seen[x] = i
`,
  },
  {
    key: 'two-sum-python-wrong',
    slug: 'two-sum',
    language: 'PYTHON',
    expected: 'WRONG_ANSWER',
    source: `input()
input()
print(0, 0)
`,
  },
  {
    key: 'fizzbuzz-python-ok',
    slug: 'fizzbuzz',
    language: 'PYTHON',
    expected: 'ACCEPTED',
    source: `n = int(input())
out = []
for i in range(1, n + 1):
    out.append("FizzBuzz" if i % 15 == 0 else "Fizz" if i % 3 == 0 else "Buzz" if i % 5 == 0 else str(i))
print("\\n".join(out))
`,
  },
  {
    key: 'fizzbuzz-java-ok',
    slug: 'fizzbuzz',
    language: 'JAVA',
    expected: 'ACCEPTED',
    source: `import java.util.*;

public class Main {
    public static void main(String[] args) {
        int n = new Scanner(System.in).nextInt();
        StringBuilder out = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            out.append(i % 15 == 0 ? "FizzBuzz" : i % 3 == 0 ? "Fizz" : i % 5 == 0 ? "Buzz" : String.valueOf(i)).append('\\n');
        }
        System.out.print(out);
    }
}
`,
  },
  {
    key: 'fizzbuzz-java-wrong',
    slug: 'fizzbuzz',
    language: 'JAVA',
    expected: 'WRONG_ANSWER',
    source: `import java.util.*;

public class Main {
    public static void main(String[] args) {
        int n = new Scanner(System.in).nextInt();
        for (int i = 1; i <= n; i++) {
            System.out.println(i % 3 == 0 ? "Fizz" : i % 5 == 0 ? "Buzz" : String.valueOf(i));
        }
    }
}
`,
  },
  {
    key: 'reverse-string-python-ok',
    slug: 'reverse-string',
    language: 'PYTHON',
    expected: 'ACCEPTED',
    source: `import sys
line = sys.stdin.readline().rstrip("\\n")
print(line[::-1])
`,
  },
  {
    key: 'reverse-string-java-wrong',
    slug: 'reverse-string',
    language: 'JAVA',
    expected: 'WRONG_ANSWER',
    source: `import java.io.*;

public class Main {
    public static void main(String[] args) throws IOException {
        String line = new BufferedReader(new InputStreamReader(System.in)).readLine();
        System.out.println(line);
    }
}
`,
  },
];
