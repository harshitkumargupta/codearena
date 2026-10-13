package com.codearena.worker.sandbox;

import com.codearena.common.Language;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SandboxRunner {

    private static final Logger log = LoggerFactory.getLogger(SandboxRunner.class);

    private static final String JAVA_SOURCE = "Main.java";
    private static final String PYTHON_SOURCE = "Main.py";
    private static final String STDIN_FILE = "stdin.txt";
    private static final String COMPILE_OUTPUT_DIR = "/out";
    private static final String PYTHON_PYCACHE_DIR = ContainerExecutor.WORK_DIR + "/pycache";
    private static final String LAUNCHER = "judge-run";
    private static final String JAVA_STACK_SIZE = "-Xss64m";
    private static final String JAVA_OOM_MARKER = "java.lang.OutOfMemoryError";
    private static final String JAVA_THREAD_LIMIT_MARKER = "unable to create native thread";
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final ContainerExecutor executor;
    private final SandboxProperties properties;

    public SandboxRunner(ContainerExecutor executor, SandboxProperties properties) {
        this.executor = executor;
        this.properties = properties;
    }

    public SandboxResult run(Language language, String source, String stdin, int timeLimitMs, int memoryLimitMb) {
        CompileResult compiled = compile(language, source);
        return compiled.succeeded()
                ? run(compiled.program(), stdin, timeLimitMs, memoryLimitMb)
                : compiled.failure();
    }

    public CompileResult compile(Language language, String source) {
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);
        boolean java = language == Language.JAVA;
        String sourceFile = java ? JAVA_SOURCE : PYTHON_SOURCE;
        try {
            ContainerRun run = executor.execute(new ContainerSpec(
                    java ? properties.javaImage() : properties.pythonImage(),
                    java ? javacCommand() : pythonCompileCommand(),
                    properties.compileMemoryMb() * BYTES_PER_MB,
                    properties.compileTimeLimitMs(),
                    Map.of(sourceFile, sourceBytes),
                    java ? COMPILE_OUTPUT_DIR : null));
            if (run.timedOut() || run.oomKilled() || run.outputLimitExceeded() || run.exitCode() != 0
                    || (java && run.outputFiles().isEmpty())) {
                String stderr = run.timedOut() ? "Compilation timed out\n" + run.stderr() : run.stderr();
                return CompileResult.failed(new SandboxResult(SandboxOutcome.COMPILE_ERROR, run.stdout(), stderr,
                        run.exitCode(), run.wallTimeMs(), null, null, run.outputLimitExceeded()));
            }
            Map<String, byte[]> files = java ? run.outputFiles() : Map.of(PYTHON_SOURCE, sourceBytes);
            return CompileResult.success(new Program(language, files));
        } catch (RuntimeException ex) {
            log.error("Sandbox compilation failed for {}", language, ex);
            return CompileResult.failed(SandboxResult.internalError());
        }
    }

    public SandboxResult run(Program program, String stdin, int timeLimitMs, int memoryLimitMb) {
        Map<String, byte[]> files = new HashMap<>(program.files());
        files.put(STDIN_FILE, stdin.getBytes(StandardCharsets.UTF_8));
        boolean java = program.language() == Language.JAVA;
        long containerMemoryMb = java ? memoryLimitMb + properties.javaOverheadMb() : memoryLimitMb;
        try {
            ContainerRun run = executor.execute(new ContainerSpec(
                    java ? properties.javaImage() : properties.pythonImage(),
                    java ? javaRunCommand(memoryLimitMb) : pythonRunCommand(),
                    containerMemoryMb * BYTES_PER_MB,
                    (long) timeLimitMs + properties.killGraceMs(),
                    files,
                    null));
            boolean launcherFinished = !run.timedOut() && !run.outputLimitExceeded();
            RunMetadata metadata = launcherFinished
                    ? RunMetadata.parse(run.stderr())
                    : new RunMetadata(run.stderr(), null, null);
            return new SandboxResult(classify(run, metadata.stderr(), java, timeLimitMs), run.stdout(),
                    metadata.stderr(), run.exitCode(), run.wallTimeMs(), metadata.peakMemoryKb(),
                    metadata.cpuTimeMs(), run.outputLimitExceeded());
        } catch (RuntimeException ex) {
            log.error("Sandbox run failed for {}", program.language(), ex);
            return SandboxResult.internalError();
        }
    }

    private static SandboxOutcome classify(ContainerRun run, String stderr, boolean java, int timeLimitMs) {
        if (run.outputLimitExceeded()) {
            return SandboxOutcome.OUTPUT_LIMIT;
        }
        if (run.timedOut()) {
            return SandboxOutcome.TIMEOUT;
        }
        boolean javaHeapExhausted = java && run.exitCode() != 0 && stderr.contains(JAVA_OOM_MARKER)
                && !stderr.contains(JAVA_THREAD_LIMIT_MARKER);
        if (run.oomKilled() || javaHeapExhausted) {
            return SandboxOutcome.OOM;
        }
        if (run.wallTimeMs() > timeLimitMs) {
            return SandboxOutcome.TIMEOUT;
        }
        return run.exitCode() == 0 ? SandboxOutcome.OK : SandboxOutcome.RUNTIME_ERROR;
    }

    private List<String> javacCommand() {
        return List.of("javac",
                "-J-Xmx" + properties.compileMemoryMb() / 2 + "m",
                "-J-XX:+UseSerialGC",
                "-J-XX:TieredStopAtLevel=1",
                "-J-XX:-UsePerfData",
                "-encoding", "UTF-8",
                "-d", COMPILE_OUTPUT_DIR,
                ContainerExecutor.INPUT_DIR + "/" + JAVA_SOURCE);
    }

    private static List<String> javaRunCommand(int heapMb) {
        return List.of(LAUNCHER, "java",
                "-Xmx" + heapMb + "m",
                JAVA_STACK_SIZE,
                "-XX:+UseSerialGC",
                "-XX:-UsePerfData",
                "-cp", ContainerExecutor.INPUT_DIR,
                "Main");
    }

    private static List<String> pythonCompileCommand() {
        return List.of("python3", "-X", "pycache_prefix=" + PYTHON_PYCACHE_DIR, "-m", "py_compile",
                ContainerExecutor.INPUT_DIR + "/" + PYTHON_SOURCE);
    }

    private static List<String> pythonRunCommand() {
        return List.of(LAUNCHER, "python3", "-I", ContainerExecutor.INPUT_DIR + "/" + PYTHON_SOURCE);
    }
}
