package com.codearena.worker.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.codearena.common.Language;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SandboxRunnerTest extends DockerSandboxTestBase {

    private static final int TIME_LIMIT_MS = 3000;
    private static final int MEMORY_LIMIT_MB = 256;

    @ParameterizedTest
    @EnumSource(Language.class)
    void helloWorldReadsStdin(Language language) {
        String code = source(language, """
                import java.io.*;

                public class Main {
                    public static void main(String[] args) throws IOException {
                        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                        System.out.println("Hello, " + in.readLine().trim() + "!");
                    }
                }
                """, """
                print(f"Hello, {input().strip()}!")
                """);

        SandboxResult result = runner.run(language, code, "Ada\n", TIME_LIMIT_MS, MEMORY_LIMIT_MB);

        assertThat(result.outcome()).isEqualTo(SandboxOutcome.OK);
        assertThat(result.stdout()).isEqualTo("Hello, Ada!\n");
        assertThat(result.stderr()).isEmpty();
        assertThat(result.exitCode()).isZero();
        assertThat(result.wallTimeMs()).isPositive().isLessThan(TIME_LIMIT_MS);
        assertThat(result.peakMemoryKb()).isPositive();
        assertThat(result.cpuTimeMs()).isNotNull();
        assertThat(result.outputTruncated()).isFalse();
    }

    @Test
    void javaCompileErrorIsReported() {
        SandboxResult result = runner.run(Language.JAVA, """
                public class Main {
                    public static void main(String[] args) {
                        int x = "not a number";
                    }
                }
                """, "", TIME_LIMIT_MS, MEMORY_LIMIT_MB);

        assertThat(result.outcome()).isEqualTo(SandboxOutcome.COMPILE_ERROR);
        assertThat(result.exitCode()).isNotZero();
        assertThat(result.stderr()).contains("incompatible types");
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void runtimeExceptionGivesNonZeroExit(Language language) {
        String code = source(language, """
                public class Main {
                    public static void main(String[] args) {
                        throw new IllegalStateException("boom");
                    }
                }
                """, """
                raise ValueError("boom")
                """);

        SandboxResult result = runner.run(language, code, "", TIME_LIMIT_MS, MEMORY_LIMIT_MB);

        assertThat(result.outcome()).isEqualTo(SandboxOutcome.RUNTIME_ERROR);
        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.stderr()).contains(language == Language.JAVA ? "IllegalStateException: boom" : "ValueError: boom");
    }
}
