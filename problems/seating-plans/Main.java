public class Main {
    private static int n;
    private static boolean[][] conflict;
    private static boolean[] used;

    public static void main(String[] args) throws java.io.IOException {
        Reader in = new Reader();
        n = in.nextInt();
        int m = in.nextInt();
        conflict = new boolean[n + 1][n + 1];
        used = new boolean[n + 1];
        for (int i = 0; i < m; i++) {
            int a = in.nextInt();
            int b = in.nextInt();
            conflict[a][b] = true;
            conflict[b][a] = true;
        }
        System.out.println(place(0, 0));
    }

    private static long place(int count, int last) {
        if (count == n) {
            return 1;
        }
        long total = 0;
        for (int guest = 1; guest <= n; guest++) {
            if (!used[guest] && !conflict[last][guest]) {
                used[guest] = true;
                total += place(count + 1, guest);
                used[guest] = false;
            }
        }
        return total;
    }

    static final class Reader {
        private final java.io.DataInputStream in = new java.io.DataInputStream(System.in);
        private final byte[] buffer = new byte[1 << 16];
        private int length;
        private int pointer;

        private int read() throws java.io.IOException {
            if (pointer == length) {
                length = in.read(buffer, 0, buffer.length);
                pointer = 0;
                if (length <= 0) {
                    return -1;
                }
            }
            return buffer[pointer++];
        }

        long nextLong() throws java.io.IOException {
            int b = read();
            while (b != '-' && (b < '0' || b > '9')) {
                if (b == -1) {
                    throw new java.io.EOFException();
                }
                b = read();
            }
            boolean negative = b == '-';
            if (negative) {
                b = read();
            }
            long value = 0;
            while (b >= '0' && b <= '9') {
                value = value * 10 + (b - '0');
                b = read();
            }
            return negative ? -value : value;
        }

        int nextInt() throws java.io.IOException {
            return (int) nextLong();
        }

        String next() throws java.io.IOException {
            int b = read();
            while (b == ' ' || b == '\n' || b == '\r' || b == '\t') {
                b = read();
            }
            if (b == -1) {
                throw new java.io.EOFException();
            }
            StringBuilder token = new StringBuilder();
            while (b != -1 && b != ' ' && b != '\n' && b != '\r' && b != '\t') {
                token.append((char) b);
                b = read();
            }
            return token.toString();
        }
    }
}
