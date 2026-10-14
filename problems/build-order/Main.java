import java.util.PriorityQueue;

public class Main {
    public static void main(String[] args) throws java.io.IOException {
        Reader in = new Reader();
        int n = in.nextInt();
        int m = in.nextInt();
        int[] from = new int[m];
        int[] to = new int[m];
        int[] start = new int[n + 2];
        int[] waiting = new int[n + 1];
        for (int i = 0; i < m; i++) {
            from[i] = in.nextInt();
            to[i] = in.nextInt();
            start[from[i] + 1]++;
            waiting[to[i]]++;
        }
        for (int v = 1; v <= n + 1; v++) {
            start[v] += start[v - 1];
        }
        int[] fill = start.clone();
        int[] next = new int[m];
        for (int i = 0; i < m; i++) {
            next[fill[from[i]]++] = to[i];
        }
        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int t = 1; t <= n; t++) {
            if (waiting[t] == 0) {
                ready.add(t);
            }
        }
        StringBuilder out = new StringBuilder();
        int done = 0;
        while (!ready.isEmpty()) {
            int task = ready.poll();
            out.append(task).append(' ');
            done++;
            for (int e = start[task]; e < start[task + 1]; e++) {
                if (--waiting[next[e]] == 0) {
                    ready.add(next[e]);
                }
            }
        }
        if (done == n) {
            out.setLength(out.length() - 1);
            System.out.println(out);
        } else {
            System.out.println("IMPOSSIBLE");
        }
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
