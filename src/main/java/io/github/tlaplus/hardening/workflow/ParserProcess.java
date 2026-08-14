package io.github.tlaplus.hardening.workflow;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Parent-side handle for one persistent isolated SANY JVM. */
final class ParserProcess implements AutoCloseable {
    private final Process process;
    private final DataInputStream input;
    private final DataOutputStream output;

    private ParserProcess(Process process) {
        this.process = process;
        this.input = new DataInputStream(new BufferedInputStream(process.getInputStream()));
        this.output = new DataOutputStream(new BufferedOutputStream(process.getOutputStream()));
    }

    static ParserProcess start(Duration timeout) throws WorkflowException, InterruptedException {
        var java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var command = new ProcessBuilder(
                        java,
                        "-cp",
                        System.getProperty("java.class.path"),
                        ParserWorkerMain.class.getName())
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        final Process process;
        try {
            process = command.start();
        } catch (IOException exception) {
            throw new WorkflowException("cannot start parser worker JVM", exception);
        }

        var worker = new ParserProcess(process);
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
            throw new WorkflowException("parser worker did not start within " + timeout, exception);
        } catch (ExecutionException exception) {
            worker.close();
            throw new WorkflowException(
                    "parser worker failed during startup", exception.getCause());
        }
    }

    ParserResult parse(String source, Duration timeout)
            throws WorkflowException, InterruptedException {
        var bytes = source.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > ParserWorkerProtocol.MAXIMUM_MESSAGE_BYTES) {
            throw new WorkflowException("generated specification exceeds parser protocol limit");
        }
        if (!process.isAlive()) {
            return crashed("parser worker exited before accepting the input");
        }
        try {
            output.writeInt(bytes.length);
            output.write(bytes);
            output.flush();
        } catch (IOException exception) {
            close();
            return crashed("parser worker died while accepting the input");
        }

        try {
            var result = await(this::readResult, timeout);
            if (result.outcome() == ParserResult.Outcome.CRASH) {
                close();
            }
            return result;
        } catch (TimeoutException exception) {
            close();
            return crashed("parser timed out after " + timeout);
        } catch (ExecutionException exception) {
            var cause = exception.getCause();
            if (cause instanceof EOFException || !process.isAlive()) {
                close();
                return crashed("parser worker exited while processing the input");
            }
            close();
            throw new WorkflowException("parser worker protocol failed", cause);
        }
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

    @Override
    public void close() {
        if (process.isAlive()) {
            try {
                output.writeInt(ParserWorkerProtocol.STOP);
                output.flush();
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroy();
                }
                if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
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
    }
}
