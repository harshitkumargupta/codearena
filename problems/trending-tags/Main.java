import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class Main {
    public static void main(String[] args) throws java.io.IOException {
        Reader in = new Reader();
        int n = in.nextInt();
        int k = in.nextInt();
        Map<String, Integer> counts = new HashMap<>();
        for (int i = 0; i < n; i++) {
            counts.merge(in.next(), 1, Integer::sum);
        }
        PriorityQueue<Map.Entry<String, Integer>> worstFirst = new PriorityQueue<>((a, b) -> {
            if (!a.getValue().equals(b.getValue())) {
                return Integer.compare(a.getValue(), b.getValue());
            }
            return b.getKey().compareTo(a.getKey());
        });
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            worstFirst.add(entry);
            if (worstFirst.size() > k) {
                worstFirst.poll();
            }
        }
        List<Map.Entry<String, Integer>> top = new ArrayList<>();
        while (!worstFirst.isEmpty()) {
            top.add(worstFirst.poll());
        }
        StringBuilder out = new StringBuilder();
        for (int i = top.size() - 1; i >= 0; i--) {
            out.append(top.get(i).getKey()).append(' ').append(top.get(i).getValue()).append('\n');
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
