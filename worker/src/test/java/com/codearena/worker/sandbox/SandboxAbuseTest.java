package com.codearena.worker.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import com.codearena.common.Language;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SandboxAbuseTest extends DockerSandboxTestBase {

    private static final int TIME_LIMIT_MS = 3000;
    private static final int MEMORY_LIMIT_MB = 256;
    private static final long CONTAINER_OVERHEAD_MS = 5000;

    @ParameterizedTest
    @EnumSource(Language.class)
    void infiniteLoopIsKilledAsTimeout(Language language) {
        Program program = compile(language, source(language, """
                public class Main {
                    public static void main(String[] args) {
                        while (true) { }
                    }
                }
                """, """
                while True:
                    pass
                """));
        int timeLimitMs = 1000;

        long started = System.nanoTime();
        SandboxResult result = runner.run(program, "", timeLimitMs, MEMORY_LIMIT_MB);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(result.outcome()).isEqualTo(SandboxOutcome.TIMEOUT);
        assertThat(elapsedMs).isLessThan(timeLimitMs + properties.killGraceMs() + CONTAINER_OVERHEAD_MS);
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void memoryHogIsReportedAsOom(Language language) {
        String code = source(language, """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        List<byte[]> hog = new ArrayList<>();
                        while (true) hog.add(new byte[8 << 20]);
                    }
                }
                """, """
                hog = []
                while True:
                    hog.append(bytearray(8 << 20))
                """);

        SandboxResult result = runner.run(language, code, "", TIME_LIMIT_MS, 128);

        assertThat(result.outcome()).isEqualTo(SandboxOutcome.OOM);
        assertThat(result.exitCode()).isNotZero();
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void processSpawningIsCappedByPidsLimit(Language language) {
        String code = source(language, """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        List<Process> children = new ArrayList<>();
                        try {
                            while (true) children.add(new ProcessBuilder("sleep", "60").start());
                        } catch (Exception e) {
                            System.out.println("spawned " + children.size());
                            System.out.println(e.getMessage());
                        }
                    }
                }
                """, """
                import os, time

                children = 0
                try:
                    while True:
                        if os.fork() == 0:
                            time.sleep(60)
                            os._exit(0)
                        children += 1
                except OSError as e:
                    print("spawned", children)
                    print(e)
                """);

        SandboxResult result = runner.run(language, code, "", 10_000, MEMORY_LIMIT_MB);

        assertThat(result.outcome()).isEqualTo(SandboxOutcome.OK);
        assertThat(result.stdout()).contains("Resource temporarily unavailable");
        int spawned = Integer.parseInt(result.stdout().lines().findFirst().orElseThrow().split(" ")[1]);
        assertThat(spawned).isPositive().isLessThan(properties.pidsLimit());
    }

    @Test
    void forkBombIsContainedAndWorkerSurvives() {
        SandboxResult bomb = runner.run(Language.PYTHON, """
                import os
                while True:
                    os.fork()
                """, "", 2000, MEMORY_LIMIT_MB);

        assertThat(bomb.outcome()).isIn(SandboxOutcome.TIMEOUT, SandboxOutcome.RUNTIME_ERROR);
        assertThat(bomb.stderr()).contains("Resource temporarily unavailable");

        SandboxResult after = runner.run(Language.PYTHON, "print('still alive')", "", TIME_LIMIT_MS, MEMORY_LIMIT_MB);
        assertThat(after.outcome()).isEqualTo(SandboxOutcome.OK);
        assertThat(after.stdout()).isEqualTo("still alive\n");
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void outboundNetworkIsBlocked(Language language) {
        String code = source(language, """
                import java.net.*;

                public class Main {
                    public static void main(String[] args) {
                        try (Socket socket = new Socket()) {
                            socket.connect(new InetSocketAddress("1.1.1.1", 53), 3000);
                            System.out.println("CONNECTED");
                        } catch (Exception e) {
                            System.out.println("BLOCKED");
                        }
                        try {
                            InetAddress.getByName("example.com");
                            System.out.println("RESOLVED");
                        } catch (Exception e) {
                            System.out.println("DNS_BLOCKED");
                        }
                    }
                }
                """, """
                import socket

                try:
                    socket.create_connection(("1.1.1.1", 53), timeout=3)
                    print("CONNECTED")
                except OSError:
                    print("BLOCKED")
                try:
                    socket.getaddrinfo("example.com", 80)
                    print("RESOLVED")
                except OSError:
                    print("DNS_BLOCKED")
                """);

        SandboxResult result = runner.run(language, code, "", 10_000, MEMORY_LIMIT_MB);

        assertThat(result.outcome()).isEqualTo(SandboxOutcome.OK);
        assertThat(result.stdout()).isEqualTo("BLOCKED\nDNS_BLOCKED\n");
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void onlyTmpfsIsWritable(Language language) {
        String code = source(language, """
                import java.nio.file.*;

                public class Main {
                    public static void main(String[] args) {
                        String[] paths = {"/input/evil.txt", "/etc/evil.txt", "/evil.txt",
                                "/usr/local/bin/evil", "/nonexistent/evil.txt", "/tmp/ok.txt"};
                        for (String path : paths) {
                            try {
                                Files.writeString(Path.of(path), "x");
                                System.out.println(path + " OK");
                            } catch (Exception e) {
                                System.out.println(path + " FAIL");
                            }
                        }
                    }
                }
                """, """
                paths = ["/input/evil.txt", "/etc/evil.txt", "/evil.txt",
                         "/usr/local/bin/evil", "/nonexistent/evil.txt", "/tmp/ok.txt"]
                for path in paths:
                    try:
                        with open(path, "w") as f:
                            f.write("x")
                        print(path, "OK")
                    except OSError:
                        print(path, "FAIL")
                """);

        SandboxResult result = runner.run(language, code, "", TIME_LIMIT_MS, MEMORY_LIMIT_MB);

        assertThat(result.outcome()).isEqualTo(SandboxOutcome.OK);
        assertThat(result.stdout().lines()).containsExactly(
                "/input/evil.txt FAIL",
                "/etc/evil.txt FAIL",
                "/evil.txt FAIL",
                "/usr/local/bin/evil FAIL",
                "/nonexistent/evil.txt FAIL",
                "/tmp/ok.txt OK");
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void hugeStdoutIsTruncatedAndKilled(Language language) {
        String code = source(language, """
                import java.util.Arrays;

                public class Main {
                    public static void main(String[] args) {
                        byte[] chunk = new byte[8192];
                        Arrays.fill(chunk, (byte) 'x');
                        while (true) System.out.write(chunk, 0, chunk.length);
                    }
                }
                """, """
                import sys

                chunk = "x" * 8192
                while True:
                    sys.stdout.write(chunk)
                """);
        int timeLimitMs = 10_000;

        long started = System.nanoTime();
        SandboxResult result = runner.run(language, code, "", timeLimitMs, MEMORY_LIMIT_MB);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(result.outcome()).isEqualTo(SandboxOutcome.OUTPUT_LIMIT);
        assertThat(result.outputTruncated()).isTrue();
        assertThat(result.stdout()).hasSize(properties.outputLimitBytes()).matches("x+");
        assertThat(elapsedMs).as("killed on the output cap, not the time limit").isLessThan(timeLimitMs);
    }

    @ParameterizedTest
    @EnumSource(Language.class)
    void filesDoNotLeakBetweenRuns(Language language) {
        Program program = compile(language, source(language, """
                import java.nio.file.*;
                import java.util.Scanner;

                public class Main {
                    public static void main(String[] args) throws Exception {
                        String command = new Scanner(System.in).next();
                        Path secret = Path.of("/tmp/secret.txt");
                        if (command.equals("write")) {
                            Files.writeString(secret, "s3cret");
                            System.out.println("WROTE");
                        } else {
                            System.out.println(Files.exists(secret) ? "FOUND " + Files.readString(secret) : "MISSING");
                        }
                    }
                }
                """, """
                import os

                command = input().strip()
                if command == "write":
                    with open("/tmp/secret.txt", "w") as f:
                        f.write("s3cret")
                    print("WROTE")
                else:
                    print("FOUND" if os.path.exists("/tmp/secret.txt") else "MISSING")
                """));

        SandboxResult first = runner.run(program, "write\n", TIME_LIMIT_MS, MEMORY_LIMIT_MB);
        SandboxResult second = runner.run(program, "read\n", TIME_LIMIT_MS, MEMORY_LIMIT_MB);

        assertThat(first.stdout()).isEqualTo("WROTE\n");
        assertThat(second.outcome()).isEqualTo(SandboxOutcome.OK);
        assertThat(second.stdout()).isEqualTo("MISSING\n");
    }
}
