import java.util.PriorityQueue;

public class Main {
    public static void main(String[] args) throws java.io.IOException {
        Reader in = new Reader();
        int n = in.nextInt();
        int m = in.nextInt();
        int[] from = new int[m];
        int[] to = new int[m];
        int[] toll = new int[m];
        int[] start = new int[n + 2];
        for (int i = 0; i < m; i++) {
            from[i] = in.nextInt();
            to[i] = in.nextInt();
            toll[i] = in.nextInt();
            start[from[i] + 1]++;
        }
        for (int v = 1; v <= n + 1; v++) {
            start[v] += start[v - 1];
        }
        int[] fill = start.clone();
        int[] edgeTo = new int[m];
        int[] edgeToll = new int[m];
        for (int i = 0; i < m; i++) {
            int slot = fill[from[i]]++;
            edgeTo[slot] = to[i];
            edgeToll[slot] = toll[i];
        }
        long[] dist = new long[n + 1];
        java.util.Arrays.fill(dist, -1);
        PriorityQueue<long[]> heap = new PriorityQueue<>((x, y) -> Long.compare(x[0], y[0]));
        heap.add(new long[] {0, 1});
        while (!heap.isEmpty()) {
            long[] top = heap.poll();
            int node = (int) top[1];
            if (dist[node] >= 0) {
                continue;
            }
            dist[node] = top[0];
            if (node == n) {
                break;
            }
            for (int e = start[node]; e < start[node + 1]; e++) {
                if (dist[edgeTo[e]] < 0) {
                    heap.add(new long[] {top[0] + edgeToll[e], edgeTo[e]});
                }
            }
        }
        System.out.println(dist[n]);
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
