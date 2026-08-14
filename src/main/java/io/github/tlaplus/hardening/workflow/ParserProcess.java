package io.github.tlaplus.hardening.workflow;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Parent-side handle for one persistent isolated SANY JVM. */
final class ParserProcess implements AutoCloseable {
    private static final int MAXIMUM_STANDARD_ERROR_BYTES = 64 * 1024;
    private static final Duration PROCESS_TERMINATION_TIMEOUT = Duration.ofMillis(500);
    private static final Duration STANDARD_ERROR_DRAIN_TIMEOUT = Duration.ofSeconds(1);
    private static final LinkOption[] NO_FOLLOW_LINKS = {LinkOption.NOFOLLOW_LINKS};

    private final Process process;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final BoundedErrorCapture standardError;
    private final Path temporaryDirectory;
    private boolean closed;

    private ParserProcess(Process process, Path temporaryDirectory) {
        this.process = process;
        this.input = new DataInputStream(new BufferedInputStream(process.getInputStream()));
        this.output = new DataOutputStream(new BufferedOutputStream(process.getOutputStream()));
        this.standardError = new BoundedErrorCapture(process.getErrorStream());
        this.temporaryDirectory = temporaryDirectory;
    }

    static ParserProcess start(Path scratchDirectory, Duration timeout)
            throws WorkflowException, InterruptedException {
        return start(scratchDirectory, timeout, ParserWorkerMain.class);
    }

    static ParserProcess start(Path scratchDirectory, Duration timeout, Class<?> workerMain)
            throws WorkflowException, InterruptedException {
        Objects.requireNonNull(scratchDirectory, "scratchDirectory");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(workerMain, "workerMain");
        var normalizedScratch = scratchDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedScratch, NO_FOLLOW_LINKS)) {
            throw new WorkflowException(
                    "parser scratch directory does not exist: " + normalizedScratch);
        }

        final Path temporaryDirectory;
        try {
            temporaryDirectory = Files.createTempDirectory(normalizedScratch, "worker-");
        } catch (IOException exception) {
            throw new WorkflowException(
                    "cannot create parser worker scratch directory", exception);
        }

        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var command = new ProcessBuilder(
                        java,
                        "-Djava.io.tmpdir=" + temporaryDirectory,
                        "-cp",
                        System.getProperty("java.class.path"),
                        workerMain.getName());
        final Process process;
        try {
            process = command.start();
        } catch (IOException exception) {
            deleteAfterFailure(temporaryDirectory, exception);
            throw new WorkflowException("cannot start parser worker JVM", exception);
        }

        var worker = new ParserProcess(process, temporaryDirectory);
        try {
            var handshake = worker.await(
                    () -> new int[] {worker.input.readInt(), worker.input.readInt()}, timeout);
            if (handshake[0] != ParserWorkerProtocol.MAGIC
                    || handshake[1] != ParserWorkerProtocol.VERSION) {
                throw new WorkflowException("parser worker protocol handshake failed");
            }
            return worker;
        } catch (TimeoutException exception) {
            worker.close();
            throw new WorkflowException(
                    worker.withStandardError(
                            "parser worker did not start within " + timeout),
                    exception);
        } catch (ExecutionException exception) {
            worker.close();
            throw new WorkflowException(
                    worker.withStandardError("parser worker failed during startup"),
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

    ParserResult parse(String source, Duration timeout)
            throws WorkflowException, InterruptedException {
        var bytes = source.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > ParserWorkerProtocol.MAXIMUM_MESSAGE_BYTES) {
            throw new WorkflowException("generated specification exceeds parser protocol limit");
        }
        if (!process.isAlive()) {
            return crashAndClose("parser worker exited before accepting the input");
        }
        try {
            output.writeInt(bytes.length);
            output.write(bytes);
            output.flush();
        } catch (IOException exception) {
            return crashAndClose("parser worker died while accepting the input");
        }

        try {
            var result = await(this::readResult, timeout);
            if (result.outcome() == ParserResult.Outcome.CRASH) {
                close();
            }
            return result;
        } catch (TimeoutException exception) {
            return crashAndClose("parser timed out after " + timeout);
        } catch (ExecutionException exception) {
            var cause = exception.getCause();
            if (cause instanceof EOFException || !process.isAlive()) {
                return crashAndClose("parser worker exited while processing the input");
            }
            close();
            throw new WorkflowException(
                    withStandardError("parser worker protocol failed"), cause);
        }
    }

    private ParserResult crashAndClose(String diagnostic) {
        close();
        return crashed(withStandardError(diagnostic));
    }

    private ParserResult readResult() throws IOException, WorkflowException {
        var outcome = ParserResult.Outcome.fromProtocolCode(input.readInt());
        var diagnosticLength = input.readInt();
        if (diagnosticLength < 0
                || diagnosticLength > ParserWorkerProtocol.MAXIMUM_DIAGNOSTIC_BYTES) {
            throw new WorkflowException(
                    "parser worker returned an invalid diagnostic length: " + diagnosticLength);
        }
        var diagnosticBytes = input.readNBytes(diagnosticLength);
        if (diagnosticBytes.length != diagnosticLength) {
            throw new EOFException("truncated parser worker diagnostic");
        }
        return new ParserResult(
                outcome, new String(diagnosticBytes, StandardCharsets.UTF_8));
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

    private static ParserResult crashed(String diagnostic) {
        return new ParserResult(ParserResult.Outcome.CRASH, diagnostic);
    }

    private String withStandardError(String diagnostic) {
        var captured = standardError.text();
        if (captured.isBlank()) {
            return diagnostic;
        }
        return diagnostic
                + System.lineSeparator()
                + "parser worker stderr:"
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
                output.writeInt(ParserWorkerProtocol.STOP);
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
            // The run-scoped corpus scratch owner retries cleanup after all workers stop.
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

    private static final class BoundedErrorCapture implements AutoCloseable {
        private final InputStream input;
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        private final Thread reader;
        private boolean truncated;

        private BoundedErrorCapture(InputStream input) {
            this.input = input;
            this.reader = Thread.startVirtualThread(this::read);
        }

        private void read() {
            var buffer = new byte[8192];
            try (input) {
                int length;
                while ((length = input.read(buffer)) >= 0) {
                    append(buffer, length);
                }
            } catch (IOException ignored) {
                // Closing or terminating the child process also closes its stderr pipe.
            }
        }

        private synchronized void append(byte[] bytes, int length) {
            var remaining = MAXIMUM_STANDARD_ERROR_BYTES - captured.size();
            if (remaining > 0) {
                captured.write(bytes, 0, Math.min(remaining, length));
            }
            if (length > remaining) {
                truncated = true;
            }
        }

        private synchronized String text() {
            var result = captured.toString(StandardCharsets.UTF_8);
            if (truncated) {
                result += System.lineSeparator() + "[stderr truncated]";
            }
            return result;
        }

        @Override
        public void close() {
            var interrupted = false;
            try {
                reader.join(STANDARD_ERROR_DRAIN_TIMEOUT.toMillis());
                if (reader.isAlive()) {
                    try {
                        input.close();
                    } catch (IOException ignored) {
                        // The child process may already have closed the pipe.
                    }
                    reader.join(STANDARD_ERROR_DRAIN_TIMEOUT.toMillis());
                }
            } catch (InterruptedException exception) {
                interrupted = true;
                try {
                    input.close();
                } catch (IOException ignored) {
                    // The child process may already have closed the pipe.
                }
            } finally {
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }
}
