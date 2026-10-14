import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer header = new StringTokenizer(in.readLine());
        int rows = Integer.parseInt(header.nextToken());
        int cols = Integer.parseInt(header.nextToken());
        char[] grid = new char[rows * cols];
        for (int r = 0; r < rows; r++) {
            String line = in.readLine().trim();
            line.getChars(0, cols, grid, r * cols);
        }
        int start = -1;
        int target = -1;
        for (int i = 0; i < grid.length; i++) {
            if (grid[i] == 'S') {
                start = i;
            } else if (grid[i] == 'T') {
                target = i;
            }
        }
        int[] dist = new int[grid.length];
        java.util.Arrays.fill(dist, -1);
        int[] queue = new int[grid.length];
        int head = 0;
        int tail = 0;
        dist[start] = 0;
        queue[tail++] = start;
        int[] dr = {-1, 1, 0, 0};
        int[] dc = {0, 0, -1, 1};
        while (head < tail) {
            int cell = queue[head++];
            if (cell == target) {
                break;
            }
            int r = cell / cols;
            int c = cell % cols;
            for (int d = 0; d < 4; d++) {
                int nr = r + dr[d];
                int nc = c + dc[d];
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                    int next = nr * cols + nc;
                    if (dist[next] < 0 && grid[next] != '#') {
                        dist[next] = dist[cell] + 1;
                        queue[tail++] = next;
                    }
                }
            }
        }
        System.out.println(dist[target]);
    }
}
