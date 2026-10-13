package com.codearena.it;

final class Solutions {

    static final String TWO_SUM_JAVA = """
            import java.util.*;

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
            """;

    static final String TWO_SUM_PYTHON_WRONG = """
            input()
            input()
            print(0, 0)
            """;

    private Solutions() {
    }
}
