import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws java.io.IOException {
        Reader in = new Reader();
        int n = in.nextInt();
        int limit = in.nextInt();
        int[] weights = new int[n];
        for (int i = 0; i < n; i++) {
            weights[i] = in.nextInt();
        }
        Arrays.sort(weights);
        int light = 0;
        int heavy = n - 1;
        int trips = 0;
        while (light <= heavy) {
            if (light < heavy && weights[light] + weights[heavy] <= limit) {
                light++;
            }
            heavy--;
            trips++;
        }
        System.out.println(trips);
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
