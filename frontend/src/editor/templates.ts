import type { Language } from '../api/types';

export const STARTER_TEMPLATES: Record<Language, string> = {
  JAVA: `import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class Main {
    public static void main(String[] args) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        StringTokenizer tokens = new StringTokenizer(in.readLine());

        System.out.println();
    }
}
`,
  PYTHON: `import sys


def main():
    data = sys.stdin.read().split()

    print()


main()
`,
};

export const MONACO_LANGUAGE: Record<Language, string> = { JAVA: 'java', PYTHON: 'python' };
export const LANGUAGE_LABELS: Record<Language, string> = { JAVA: 'Java 21', PYTHON: 'Python 3.12' };
