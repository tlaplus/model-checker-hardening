package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Parent-side supervisor for one isolated Java tool worker. */
public final class IsolatedWorkerProcess implements AutoCloseable {
    private static final int MAXIMUM_STANDARD_ERROR_BYTES = 64 * 1024;
    private static final Duration PROCESS_TERMINATION_TIMEOUT = Duration.ofMillis(500);
    private static final LinkOption[] NO_FOLLOW_LINKS = {LinkOption.NOFOLLOW_LINKS};

    private final String description;
    private final Process process;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final BoundedOutputCapture standardError;
    private final Path temporaryDirectory;
    private boolean closed;

    private IsolatedWorkerProcess(
            String description, Process process, Path temporaryDirectory) {
        this.description = description;
        this.process = process;
        input = new DataInputStream(new BufferedInputStream(process.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(process.getOutputStream()));
        standardError = new BoundedOutputCapture(
                process.getErrorStream(), MAXIMUM_STANDARD_ERROR_BYTES, "stderr");
        this.temporaryDirectory = temporaryDirectory;
    }

    public static IsolatedWorkerProcess start(
            Path scratchDirectory,
            Duration timeout,
            Class<?> workerMain,
            List<String> jvmArguments,
            String description)
            throws WorkflowException, InterruptedException {
        Objects.requireNonNull(scratchDirectory, "scratchDirectory");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(workerMain, "workerMain");
        Objects.requireNonNull(jvmArguments, "jvmArguments");
        if (Objects.requireNonNull(description, "description").isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        var normalizedScratch = scratchDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedScratch, NO_FOLLOW_LINKS)) {
            throw new WorkflowException(
                    description + " scratch directory does not exist: " + normalizedScratch);
        }

        final Path temporaryDirectory;
        try {
            temporaryDirectory = Files.createTempDirectory(normalizedScratch, "worker-");
        } catch (IOException exception) {
            throw new WorkflowException(
                    "cannot create " + description + " scratch directory", exception);
        }

        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(jvmArguments);
        command.add("-Djava.io.tmpdir=" + temporaryDirectory);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(workerMain.getName());
        final Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException exception) {
            deleteAfterFailure(temporaryDirectory, exception);
            throw new WorkflowException("cannot start " + description, exception);
        }

        var worker = new IsolatedWorkerProcess(description, process, temporaryDirectory);
        try {
            var handshake = worker.await(
                    () -> new int[] {worker.input.readInt(), worker.input.readInt()}, timeout);
            if (handshake[0] != ToolWorkerProtocol.MAGIC
                    || handshake[1] != ToolWorkerProtocol.VERSION) {
                throw new WorkflowException(description + " protocol handshake failed");
            }
            return worker;
        } catch (TimeoutException exception) {
            worker.close();
            throw new WorkflowException(
                    worker.withStandardError(description + " did not start within " + timeout),
                    exception);
        } catch (ExecutionException exception) {
            worker.close();
            throw new WorkflowException(
                    worker.withStandardError(description + " failed during startup"),
                    exception.getCause());
        } catch (WorkflowException exception) {
            worker.close();
            throw new WorkflowException(
                    worker.withStandardError(exception.getMessage()), exception);
        } catch (InterruptedException exception) {
            worker.close();
            throw exception;
        }
    }

    public ToolResult request(String source, Duration timeout)
            throws WorkflowException, InterruptedException {
        var bytes = Objects.requireNonNull(source, "source").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > ToolWorkerProtocol.MAXIMUM_MESSAGE_BYTES) {
            throw new WorkflowException(
                    "generated specification exceeds worker protocol limit");
        }
        if (!process.isAlive()) {
            return crashAndClose(description + " exited before accepting the input");
        }
        try {
            output.writeInt(bytes.length);
            output.write(bytes);
            output.flush();
        } catch (IOException exception) {
            return crashAndClose(description + " died while accepting the input");
        }

        try {
            var result = await(this::readResult, timeout);
            if (result.outcome() == StageOutcome.CRASH) {
                close();
                return new ToolResult(
                        StageOutcome.CRASH, withStandardError(result.diagnostic()));
            }
            return result;
        } catch (TimeoutException exception) {
            return crashAndClose(description + " timed out after " + timeout);
        } catch (ExecutionException exception) {
            var cause = exception.getCause();
            if (cause instanceof EOFException || !process.isAlive()) {
                return crashAndClose(description + " exited while processing the input");
            }
            close();
            throw new WorkflowException(
                    withStandardError(description + " protocol failed"), cause);
        }
    }

    private ToolResult crashAndClose(String diagnostic) {
        close();
        return new ToolResult(StageOutcome.CRASH, withStandardError(diagnostic));
    }

    private ToolResult readResult() throws IOException, WorkflowException {
        var outcome = StageOutcome.fromProtocolCode(input.readInt());
        var encodedFailureCode = input.readInt();
        var failureCode = encodedFailureCode == ToolWorkerProtocol.NO_FAILURE_CODE
                ? Optional.<CheckerFailureCode>empty()
                : decodeFailureCode(encodedFailureCode);
        var diagnosticLength = input.readInt();
        if (diagnosticLength < 0
                || diagnosticLength > ToolWorkerProtocol.MAXIMUM_DIAGNOSTIC_BYTES) {
            throw new WorkflowException(
                    "worker returned an invalid diagnostic length: " + diagnosticLength);
        }
        var diagnosticBytes = input.readNBytes(diagnosticLength);
        if (diagnosticBytes.length != diagnosticLength) {
            throw new EOFException("truncated worker diagnostic");
        }
        try {
            return new ToolResult(
                    outcome,
                    failureCode,
                    new String(diagnosticBytes, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException exception) {
            throw new WorkflowException(
                    "worker returned an invalid failure classification", exception);
        }
    }

    private static Optional<CheckerFailureCode> decodeFailureCode(int encodedCode)
            throws WorkflowException {
        try {
            return Optional.of(CheckerFailureCode.fromEncodedCode(encodedCode));
        } catch (IllegalArgumentException exception) {
            throw new WorkflowException(
                    "worker returned an unknown failure code: " + encodedCode, exception);
        }
    }

    private <T> T await(Callable<T> operation, Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        var task = new FutureTask<>(operation);
        Thread.startVirtualThread(task);
        try {
            return task.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException | TimeoutException exception) {
            task.cancel(true);
            throw exception;
        }
    }

    private String withStandardError(String diagnostic) {
        var captured = standardError.text();
        if (captured.isBlank()) {
            return diagnostic;
        }
        return diagnostic
                + System.lineSeparator()
                + description
                + " stderr:"
                + System.lineSeparator()
                + captured.stripTrailing();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (process.isAlive()) {
            try {
                output.writeInt(ToolWorkerProtocol.STOP);
                output.flush();
                if (!process.waitFor(
                        PROCESS_TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroy();
                }
                if (!process.waitFor(
                        PROCESS_TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                    process.waitFor(
                            PROCESS_TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                }
            } catch (IOException exception) {
                process.destroyForcibly();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
        try {
            input.close();
        } catch (IOException ignored) {
            // The child process owns the other end and may already be gone.
        }
        try {
            output.close();
        } catch (IOException ignored) {
            // The child process owns the other end and may already be gone.
        }
        standardError.close();
        try {
            deleteRecursively(temporaryDirectory);
        } catch (IOException ignored) {
            // The run-scoped corpus scratch owner retries after all workers stop.
        }
        closed = true;
    }

    private static void deleteAfterFailure(Path directory, Throwable failure) {
        try {
            deleteRecursively(directory);
        } catch (IOException cleanupException) {
            failure.addSuppressed(cleanupException);
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
