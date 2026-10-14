public class Main {
    public static void main(String[] args) throws java.io.IOException {
        Reader in = new Reader();
        int n = in.nextInt();
        int m = in.nextInt();
        int[] from = new int[m];
        int[] to = new int[m];
        int[] degree = new int[n + 2];
        for (int i = 0; i < m; i++) {
            from[i] = in.nextInt();
            to[i] = in.nextInt();
            degree[from[i]]++;
            degree[to[i]]++;
        }
        int[] start = new int[n + 2];
        for (int v = 1; v <= n; v++) {
            start[v + 1] = start[v] + degree[v];
        }
        int[] fill = start.clone();
        int[] adjacency = new int[2 * m];
        for (int i = 0; i < m; i++) {
            adjacency[fill[from[i]]++] = to[i];
            adjacency[fill[to[i]]++] = from[i];
        }
        boolean[] seen = new boolean[n + 1];
        int[] stack = new int[n];
        int clusters = 0;
        int largest = 0;
        for (int s = 1; s <= n; s++) {
            if (seen[s]) {
                continue;
            }
            clusters++;
            seen[s] = true;
            int top = 0;
            stack[top++] = s;
            int size = 0;
            while (top > 0) {
                int node = stack[--top];
                size++;
                for (int e = start[node]; e < start[node + 1]; e++) {
                    int other = adjacency[e];
                    if (!seen[other]) {
                        seen[other] = true;
                        stack[top++] = other;
                    }
                }
            }
            largest = Math.max(largest, size);
        }
        System.out.println(clusters + " " + largest);
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
