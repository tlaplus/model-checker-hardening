package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
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
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Parent-side supervisor for one isolated Java tool worker. */
public final class IsolatedWorkerProcess implements AutoCloseable {
    private static final int MAXIMUM_STANDARD_OUTPUT_BYTES = 64 * 1024;
    private static final int MAXIMUM_STANDARD_ERROR_BYTES = 64 * 1024;
    private static final Duration PROCESS_TERMINATION_TIMEOUT = Duration.ofMillis(500);
    private static final LinkOption[] NO_FOLLOW_LINKS = {LinkOption.NOFOLLOW_LINKS};

    private final String description;
    private final Process process;
    private final Socket protocolSocket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final BoundedOutputCapture standardOutput;
    private final BoundedOutputCapture standardError;
    private final Path temporaryDirectory;
    private String fatalErrorReports = "";
    private boolean closed;

    private IsolatedWorkerProcess(
            String description,
            Process process,
            Socket protocolSocket,
            BoundedOutputCapture standardOutput,
            BoundedOutputCapture standardError,
            Path temporaryDirectory)
            throws IOException {
        this.description = description;
        this.process = process;
        this.protocolSocket = protocolSocket;
        protocolSocket.setTcpNoDelay(true);
        input = new DataInputStream(new BufferedInputStream(protocolSocket.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(protocolSocket.getOutputStream()));
        this.standardOutput = standardOutput;
        this.standardError = standardError;
        this.temporaryDirectory = temporaryDirectory;
    }

    public static IsolatedWorkerProcess start(
            Path scratchDirectory,
            Duration timeout,
            Class<?> workerMain,
            List<Path> classpathPrefix,
            List<String> jvmArguments,
            String description)
            throws WorkflowException, InterruptedException {
        Objects.requireNonNull(scratchDirectory, "scratchDirectory");
        Objects.requireNonNull(timeout, "timeout");
        Objects.requireNonNull(workerMain, "workerMain");
        Objects.requireNonNull(classpathPrefix, "classpathPrefix");
        Objects.requireNonNull(jvmArguments, "jvmArguments");
        if (Objects.requireNonNull(description, "description").isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        var normalizedScratch = scratchDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedScratch, NO_FOLLOW_LINKS)) {
            throw new WorkflowException(
                    description + " scratch directory does not exist: " + normalizedScratch);
        }

        // Give the child private scratch storage and a private control-channel listener.
        final Path temporaryDirectory;
        try {
            temporaryDirectory = Files.createTempDirectory(normalizedScratch, "worker-");
        } catch (IOException exception) {
            throw new WorkflowException(
                    "cannot create " + description + " scratch directory", exception);
        }

        final ServerSocket listener;
        try {
            listener = new ServerSocket();
            listener.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 1);
        } catch (IOException exception) {
            deleteAfterFailure(temporaryDirectory, exception);
            throw new WorkflowException(
                    "cannot create " + description + " protocol listener", exception);
        }

        var token = UUID.randomUUID().toString();
        // Launch the worker with its tool-specific classpath first, then continuously drain
        // process output so native libraries cannot block or corrupt the control channel.
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.addAll(jvmArguments);
        command.add("-XX:ErrorFile=" + temporaryDirectory.resolve("hs_err_pid%p.log"));
        command.add("-Djava.io.tmpdir=" + temporaryDirectory);
        command.add("-D" + ToolWorkerProtocol.PORT_PROPERTY + "=" + listener.getLocalPort());
        command.add("-D" + ToolWorkerProtocol.TOKEN_PROPERTY + "=" + token);
        command.add("-cp");
        command.add(classpath(classpathPrefix));
        command.add(workerMain.getName());
        final Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException exception) {
            closeListener(listener, exception);
            deleteAfterFailure(temporaryDirectory, exception);
            throw new WorkflowException("cannot start " + description, exception);
        }

        var standardOutput = new BoundedOutputCapture(
                process.getInputStream(), MAXIMUM_STANDARD_OUTPUT_BYTES, "stdout");
        var standardError = new BoundedOutputCapture(
                process.getErrorStream(), MAXIMUM_STANDARD_ERROR_BYTES, "stderr");
        try {
            process.getOutputStream().close();
        } catch (IOException ignored) {
            // The protocol uses a private socket; the child does not consume standard input.
        }

        final Socket protocolSocket;
        // Accept the child's control connection under the same startup bound as its handshake.
        try {
            protocolSocket = await(listener::accept, timeout);
        } catch (TimeoutException exception) {
            closeListener(listener, exception);
            var fatalErrorReports = closeBeforeConnection(
                    description,
                    process,
                    standardOutput,
                    standardError,
                    temporaryDirectory);
            throw new WorkflowException(
                    withProcessOutput(
                            description,
                            standardOutput,
                            standardError,
                            fatalErrorReports,
                            description + " did not connect within " + timeout),
                    exception);
        } catch (ExecutionException exception) {
            closeListener(listener, exception);
            var fatalErrorReports = closeBeforeConnection(
                    description,
                    process,
                    standardOutput,
                    standardError,
                    temporaryDirectory);
            throw new WorkflowException(
                    withProcessOutput(
                            description,
                            standardOutput,
                            standardError,
                            fatalErrorReports,
                            description + " failed during startup"),
                    exception.getCause());
        } catch (InterruptedException exception) {
            closeListener(listener, exception);
            closeBeforeConnection(
                    description,
                    process,
                    standardOutput,
                    standardError,
                    temporaryDirectory);
            throw exception;
        }
        try {
            listener.close();
        } catch (IOException exception) {
            closeSocket(protocolSocket, exception);
            closeBeforeConnection(
                    description,
                    process,
                    standardOutput,
                    standardError,
                    temporaryDirectory);
            throw new WorkflowException(
                    "cannot close " + description + " protocol listener", exception);
        }

        final IsolatedWorkerProcess worker;
        try {
            worker = new IsolatedWorkerProcess(
                    description,
                    process,
                    protocolSocket,
                    standardOutput,
                    standardError,
                    temporaryDirectory);
        } catch (IOException exception) {
            closeSocket(protocolSocket, exception);
            closeBeforeConnection(
                    description,
                    process,
                    standardOutput,
                    standardError,
                    temporaryDirectory);
            throw new WorkflowException(
                    description + " could not open its protocol connection", exception);
        }

        // Authenticate the connected child before exposing the worker to a stage.
        try {
            var handshake = worker.await(
                    () -> new Handshake(
                            worker.input.readInt(),
                            worker.input.readInt(),
                            worker.input.readUTF()),
                    timeout);
            if (handshake.magic() != ToolWorkerProtocol.MAGIC
                    || handshake.version() != ToolWorkerProtocol.VERSION
                    || !handshake.token().equals(token)) {
                throw new WorkflowException(description + " protocol handshake failed");
            }
            return worker;
        } catch (TimeoutException exception) {
            worker.close();
            throw new WorkflowException(
                    worker.withProcessOutput(description + " did not start within " + timeout),
                    exception);
        } catch (ExecutionException exception) {
            worker.close();
            throw new WorkflowException(
                    worker.withProcessOutput(description + " failed during startup"),
                    exception.getCause());
        } catch (WorkflowException exception) {
            worker.close();
            throw new WorkflowException(
                    worker.withProcessOutput(exception.getMessage()), exception);
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
                        StageOutcome.CRASH, withProcessOutput(result.diagnostic()));
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
                    withProcessOutput(description
                            + " protocol failed: "
                            + diagnostic(cause)),
                    cause);
        }
    }

    private ToolResult crashAndClose(String diagnostic) {
        close();
        return new ToolResult(StageOutcome.CRASH, withProcessOutput(diagnostic));
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

    private static String diagnostic(Throwable failure) {
        var message = failure.getMessage();
        return message == null || message.isBlank()
                ? failure.getClass().getSimpleName()
                : message;
    }

    private static <T> T await(Callable<T> operation, Duration timeout)
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

    private String withProcessOutput(String diagnostic) {
        return withProcessOutput(
                description,
                standardOutput,
                standardError,
                fatalErrorReports,
                diagnostic);
    }

    private static String withProcessOutput(
            String description,
            BoundedOutputCapture standardOutput,
            BoundedOutputCapture standardError,
            String fatalErrorReports,
            String diagnostic) {
        var result = diagnostic;
        var capturedOutput = standardOutput.text();
        if (!capturedOutput.isBlank()) {
            result = WorkerDiagnostics.append(
                    result,
                    description
                            + " stdout:"
                            + System.lineSeparator()
                            + capturedOutput.stripTrailing());
        }
        var capturedError = standardError.text();
        if (!capturedError.isBlank()) {
            result = WorkerDiagnostics.append(
                    result,
                    description
                            + " stderr:"
                            + System.lineSeparator()
                            + capturedError.stripTrailing());
        }
        if (!fatalErrorReports.isBlank()) {
            result = WorkerDiagnostics.append(result, fatalErrorReports);
        }
        return result;
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
            protocolSocket.close();
        } catch (IOException ignored) {
            // The child process may already have closed its end of the connection.
        }
        standardOutput.close();
        standardError.close();
        fatalErrorReports = readFatalErrorReports(description, temporaryDirectory);
        try {
            deleteRecursively(temporaryDirectory);
        } catch (IOException ignored) {
            // The run-scoped corpus scratch owner retries after all workers stop.
        }
        closed = true;
    }

    private static String classpath(List<Path> prefix) {
        var entries = new ArrayList<String>(prefix.size() + 1);
        for (var path : prefix) {
            entries.add(Objects.requireNonNull(path, "classpath entry")
                    .toAbsolutePath()
                    .normalize()
                    .toString());
        }
        entries.add(System.getProperty("java.class.path"));
        return String.join(File.pathSeparator, entries);
    }

    private static String readFatalErrorReports(String description, Path temporaryDirectory) {
        try (var paths = Files.list(temporaryDirectory)) {
            var reports = paths.filter(path -> path.getFileName()
                            .toString()
                            .startsWith("hs_err_pid"))
                    .sorted()
                    .toList();
            var result = "";
            for (var report : reports) {
                result = WorkerDiagnostics.append(
                        result,
                        description
                                + " fatal error report:"
                                + System.lineSeparator()
                                + Files.readString(report));
            }
            return result;
        } catch (IOException exception) {
            return "Could not read "
                    + description
                    + " fatal error report: "
                    + exception.getMessage();
        }
    }

    private static String closeBeforeConnection(
            String description,
            Process process,
            BoundedOutputCapture standardOutput,
            BoundedOutputCapture standardError,
            Path temporaryDirectory) {
        terminate(process);
        standardOutput.close();
        standardError.close();
        var fatalErrorReports = readFatalErrorReports(description, temporaryDirectory);
        try {
            deleteRecursively(temporaryDirectory);
        } catch (IOException ignored) {
            // The run-scoped corpus scratch owner retries after all workers stop.
        }
        return fatalErrorReports;
    }

    private static void terminate(Process process) {
        if (!process.isAlive()) {
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(
                    PROCESS_TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                process.waitFor(
                        PROCESS_TERMINATION_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static void closeListener(ServerSocket listener, Throwable failure) {
        try {
            listener.close();
        } catch (IOException closeException) {
            failure.addSuppressed(closeException);
        }
    }

    private static void closeSocket(Socket socket, Throwable failure) {
        try {
            socket.close();
        } catch (IOException closeException) {
            failure.addSuppressed(closeException);
        }
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

    private record Handshake(int magic, int version, String token) {}
}
