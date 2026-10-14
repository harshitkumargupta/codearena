import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) throws java.io.IOException {
        Reader in = new Reader();
        int n = in.nextInt();
        int q = in.nextInt();
        int[] keys = new int[n];
        for (int i = 0; i < n; i++) {
            keys[i] = in.nextInt();
        }
        int[] left = new int[n];
        int[] right = new int[n];
        java.util.Arrays.fill(left, -1);
        java.util.Arrays.fill(right, -1);
        Map<Integer, Integer> depth = new HashMap<>();
        depth.put(keys[0], 0);
        for (int i = 1; i < n; i++) {
            int node = 0;
            int d = 1;
            while (true) {
                if (keys[i] < keys[node]) {
                    if (left[node] < 0) {
                        left[node] = i;
                        break;
                    }
                    node = left[node];
                } else {
                    if (right[node] < 0) {
                        right[node] = i;
                        break;
                    }
                    node = right[node];
                }
                d++;
            }
            depth.put(keys[i], d);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < q; i++) {
            out.append(depth.getOrDefault(in.nextInt(), -1)).append(i + 1 == q ? '\n' : ' ');
        }
        System.out.print(out);
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
