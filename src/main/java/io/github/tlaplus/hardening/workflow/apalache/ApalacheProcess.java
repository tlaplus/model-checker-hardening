package io.github.tlaplus.hardening.workflow.apalache;

import io.github.tlaplus.hardening.config.ApalacheStageConfig;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.worker.BoundedOutputCapture;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import io.github.tlaplus.hardening.workflow.worker.WorkerDiagnostics;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Runs one generated specification with a fresh pinned Apalache CLI process. */
final class ApalacheProcess {
    private static final int MAXIMUM_OUTPUT_BYTES = 1024 * 1024;
    private static final Duration TERMINATION_TIMEOUT = Duration.ofMillis(500);
    private static final LinkOption[] NO_FOLLOW_LINKS = {LinkOption.NOFOLLOW_LINKS};
    private static final String SPECIFICATION_FILE = "FuzzInput.tla";

    private ApalacheProcess() {}

    static ToolResult check(
            Path releaseJar,
            Path scratchDirectory,
            String source,
            ApalacheStageConfig config,
            Duration timeout)
            throws WorkflowException, InterruptedException {
        Objects.requireNonNull(releaseJar, "releaseJar");
        Objects.requireNonNull(scratchDirectory, "scratchDirectory");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(timeout, "timeout");

        final Path jobDirectory;
        try {
            jobDirectory = Files.createTempDirectory(scratchDirectory, "worker-");
        } catch (IOException exception) {
            throw new WorkflowException("cannot prepare Apalache worker input", exception);
        }
        try {
            Files.writeString(
                    jobDirectory.resolve(SPECIFICATION_FILE),
                    source,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (IOException exception) {
            try {
                deleteRecursively(jobDirectory);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new WorkflowException("cannot prepare Apalache worker input", exception);
        }

        Process process = null;
        try {
            var command = command(releaseJar, jobDirectory, config);
            try {
                process = new ProcessBuilder(command)
                        .directory(jobDirectory.toFile())
                        .redirectErrorStream(true)
                        .start();
            } catch (IOException exception) {
                throw new WorkflowException("cannot start Apalache worker", exception);
            }

            try (var output = new BoundedOutputCapture(
                    process.getInputStream(), MAXIMUM_OUTPUT_BYTES)) {
                final boolean exited;
                try {
                    exited = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                } catch (InterruptedException exception) {
                    terminate(process);
                    throw exception;
                }
                if (!exited) {
                    terminate(process);
                    output.close();
                    var diagnostic = WorkerDiagnostics.append(
                            "Apalache timed out after " + timeout, output.text());
                    diagnostic = appendFatalError(jobDirectory, diagnostic);
                    return new ToolResult(StageOutcome.CRASH, diagnostic);
                }

                output.close();
                var result = ApalacheOutcomeClassifier.classify(
                        process.exitValue(), output.text());
                if (result.outcome() == StageOutcome.CRASH) {
                    return new ToolResult(
                            StageOutcome.CRASH,
                            appendFatalError(jobDirectory, result.diagnostic()));
                }
                return result;
            }
        } finally {
            if (process != null && process.isAlive()) {
                terminate(process);
            }
            try {
                deleteRecursively(jobDirectory);
            } catch (IOException ignored) {
                // The run-scoped stage scratch owner retries after all workers stop.
            }
        }
    }

    private static ArrayList<String> command(
            Path releaseJar, Path jobDirectory, ApalacheStageConfig config) {
        var specification = jobDirectory.resolve(SPECIFICATION_FILE);
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-Xmx" + config.maximumHeapMegabytes() + "m");
        command.add("-XX:+ExitOnOutOfMemoryError");
        command.add("-XX:-UsePerfData");
        command.add("-XX:ErrorFile=" + jobDirectory.resolve("hs_err_pid%p.log"));
        command.add("-Djava.io.tmpdir=" + jobDirectory);
        command.add("-Duser.home=" + jobDirectory);
        command.add("-jar");
        command.add(releaseJar.toAbsolutePath().normalize().toString());
        command.add("--out-dir=" + jobDirectory.resolve("out"));
        command.add("check");
        command.add("--init=Init");
        command.add("--next=Next");
        command.add("--inv=Inv");
        command.add("--length=0");
        command.add("--no-deadlock");
        command.add(specification.toString());
        return command;
    }

    private static void terminate(Process process) {
        process.destroy();
        try {
            if (!process.waitFor(
                    TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(
                        TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static String appendFatalError(Path jobDirectory, String diagnostic) {
        try (var paths = Files.list(jobDirectory)) {
            var reports = paths.filter(path ->
                            path.getFileName().toString().startsWith("hs_err_pid"))
                    .sorted()
                    .toList();
            var result = diagnostic;
            for (var report : reports) {
                result = WorkerDiagnostics.append(result, Files.readString(report));
            }
            return result;
        } catch (IOException exception) {
            return WorkerDiagnostics.append(
                    diagnostic,
                    "Could not read Apalache JVM crash report: " + exception.getMessage());
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (Files.notExists(root, NO_FOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
