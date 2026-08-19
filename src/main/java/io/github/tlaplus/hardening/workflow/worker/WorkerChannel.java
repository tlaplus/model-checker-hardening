package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * The parent's end of one worker's control channel: message framing, and the deadline every read
 * runs under.
 *
 * <p>A read is bounded by running it on a virtual thread that is cancelled when the deadline
 * passes, because a hung child would otherwise block its stage worker forever.
 */
final class WorkerChannel implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;

    private WorkerChannel(Socket socket) throws IOException {
        this.socket = socket;
        socket.setTcpNoDelay(true);
        input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    static WorkerChannel open(Socket socket) throws IOException {
        return new WorkerChannel(socket);
    }

    /** Reads the child's handshake and reports whether it identifies itself correctly. */
    boolean readHandshake(Duration timeout, String token)
            throws InterruptedException, ExecutionException, TimeoutException {
        var handshake = await(
                () -> new Handshake(input.readInt(), input.readInt(), input.readUTF()), timeout);
        return handshake.magic() == ToolWorkerProtocol.MAGIC
                && handshake.version() == ToolWorkerProtocol.VERSION
                && handshake.token().equals(token);
    }

    /** Sends one request, or reports that the channel is gone. */
    boolean writeRequest(byte[] source) {
        try {
            output.writeInt(source.length);
            output.write(source);
            output.flush();
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    /** Reads one result under the supplied deadline. */
    ToolResult readResult(Duration timeout)
            throws InterruptedException, ExecutionException, TimeoutException {
        return await(this::readResult, timeout);
    }

    /** Asks the child to stop, ignoring a channel it has already closed. */
    void requestStop() throws IOException {
        output.writeInt(ToolWorkerProtocol.STOP);
        output.flush();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // The child process may already have closed its end of the connection.
        }
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

    /** Runs one blocking read under a deadline, cancelling it when the deadline passes. */
    static <T> T await(Callable<T> operation, Duration timeout)
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

    private record Handshake(int magic, int version, String token) {}
}
