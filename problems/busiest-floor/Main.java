import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws java.io.IOException {
        Reader in = new Reader();
        int t = in.nextInt();
        String[] tokens = new String[t];
        for (int i = 0; i < t; i++) {
            tokens[i] = in.next();
        }
        List<Long> floorSums = new ArrayList<>();
        int[] current = {Integer.parseInt(tokens[0])};
        int pos = 1;
        while (current.length > 0) {
            long sum = 0;
            for (int value : current) {
                sum += value;
            }
            floorSums.add(sum);
            int[] following = new int[current.length * 2];
            int size = 0;
            for (int i = 0; i < current.length; i++) {
                for (int side = 0; side < 2; side++) {
                    if (pos < t) {
                        if (!tokens[pos].equals("x")) {
                            following[size++] = Integer.parseInt(tokens[pos]);
                        }
                        pos++;
                    }
                }
            }
            current = java.util.Arrays.copyOf(following, size);
        }
        int best = 0;
        for (int i = 1; i < floorSums.size(); i++) {
            if (floorSums.get(i) > floorSums.get(best)) {
                best = i;
            }
        }
        System.out.println(best + 1);
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
