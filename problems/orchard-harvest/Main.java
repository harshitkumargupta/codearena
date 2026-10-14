import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class Main {
    private static final long MOD = 1_000_000_007L;

    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer header = new StringTokenizer(in.readLine());
        int rows = Integer.parseInt(header.nextToken());
        int cols = Integer.parseInt(header.nextToken());
        long[] bestUp = new long[cols];
        long[] waysUp = new long[cols];
        java.util.Arrays.fill(bestUp, -1);
        for (int r = 0; r < rows; r++) {
            String row = in.readLine().trim();
            long[] best = new long[cols];
            long[] ways = new long[cols];
            java.util.Arrays.fill(best, -1);
            for (int c = 0; c < cols; c++) {
                char ch = row.charAt(c);
                if (ch == '#') {
                    continue;
                }
                int apples = ch - '0';
                if (r == 0 && c == 0) {
                    best[0] = apples;
                    ways[0] = 1;
                    continue;
                }
                long top = bestUp[c];
                long left = c > 0 ? best[c - 1] : -1;
                if (top < 0 && left < 0) {
                    continue;
                }
                if (top > left) {
                    best[c] = top + apples;
                    ways[c] = waysUp[c];
                } else if (left > top) {
                    best[c] = left + apples;
                    ways[c] = ways[c - 1];
                } else {
                    best[c] = top + apples;
                    ways[c] = (waysUp[c] + ways[c - 1]) % MOD;
                }
            }
            bestUp = best;
            waysUp = ways;
        }
        System.out.println(bestUp[cols - 1] >= 0 ? bestUp[cols - 1] + " " + waysUp[cols - 1] : "-1");
    }
}
