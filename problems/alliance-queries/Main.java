public class Main {
    private static int[] parent;

    public static void main(String[] args) throws java.io.IOException {
        Reader in = new Reader();
        int n = in.nextInt();
        int q = in.nextInt();
        parent = new int[n + 1];
        int[] size = new int[n + 1];
        for (int i = 0; i <= n; i++) {
            parent[i] = i;
            size[i] = 1;
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < q; i++) {
            String kind = in.next();
            int ra = find(in.nextInt());
            int rb = find(in.nextInt());
            if (kind.equals("U")) {
                if (ra != rb) {
                    if (size[ra] < size[rb]) {
                        int t = ra;
                        ra = rb;
                        rb = t;
                    }
                    parent[rb] = ra;
                    size[ra] += size[rb];
                }
            } else {
                out.append(ra == rb ? "YES" : "NO").append('\n');
            }
        }
        System.out.print(out);
    }

    private static int find(int x) {
        int root = x;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[x] != root) {
            int next = parent[x];
            parent[x] = root;
            x = next;
        }
        return root;
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
