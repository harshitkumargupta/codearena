import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        Reader in = new Reader();
        int n = in.nextInt();
        int q = in.nextInt();
        long[] prefix = new long[n + 1];
        for (int i = 0; i < n; i++) {
            prefix[i + 1] = prefix[i] + in.nextInt();
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < q; i++) {
            int l = in.nextInt();
            int r = in.nextInt();
            out.append(prefix[r] - prefix[l - 1]).append('\n');
        }
        System.out.print(out);
    }

    static final class Reader {
        private final DataInputStream in = new DataInputStream(System.in);
        private final byte[] buffer = new byte[1 << 16];
        private int length;
        private int pointer;

        private int read() throws IOException {
            if (pointer == length) {
                length = in.read(buffer, 0, buffer.length);
                pointer = 0;
                if (length <= 0) {
                    return -1;
                }
            }
            return buffer[pointer++];
        }

        long nextLong() throws IOException {
            int b = read();
            while (b != '-' && (b < '0' || b > '9')) {
                if (b == -1) {
                    throw new EOFException();
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

        int nextInt() throws IOException {
            return (int) nextLong();
        }
    }
}
