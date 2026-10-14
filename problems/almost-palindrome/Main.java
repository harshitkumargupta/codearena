import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Main {
    public static void main(String[] args) throws IOException {
        String s = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();
        int lo = 0;
        int hi = s.length() - 1;
        while (lo < hi && s.charAt(lo) == s.charAt(hi)) {
            lo++;
            hi--;
        }
        boolean ok = lo >= hi || readsBothWays(s, lo + 1, hi) || readsBothWays(s, lo, hi - 1);
        System.out.println(ok ? "YES" : "NO");
    }

    private static boolean readsBothWays(String s, int lo, int hi) {
        while (lo < hi) {
            if (s.charAt(lo++) != s.charAt(hi--)) {
                return false;
            }
        }
        return true;
    }
}
