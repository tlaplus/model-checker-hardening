package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Parent-side supervisor for one isolated Java tool worker.
 *
 * <p>A worker is a child JVM that runs one tool over a private, token-authenticated loopback
 * connection. Every way it can fail — a timeout, an abrupt exit, a protocol violation, a fatal JVM
 * error — is reported as a crash verdict carrying whatever the child printed, and retires the
 * worker: a crashed worker is closed, never reused.
 *
 * <p>{@link WorkerLaunch} brings a worker up, {@link WorkerChannel} carries the protocol, and
 * {@link WorkerOutput} collects what the child said. This class owns their lifetime and turns
 * failures into verdicts.
 */
public final class IsolatedWorkerProcess implements AutoCloseable {
    private final String description;
    private final Process process;
    private final WorkerChannel channel;
    private final WorkerOutput output;
    private boolean closed;

    private IsolatedWorkerProcess(String description, WorkerLaunch.Launched launched) {
        this.description = description;
        process = launched.process();
        channel = launched.channel();
        output = launched.output();
    }

    /** Starts a worker and waits for it to connect and authenticate. */
    public static IsolatedWorkerProcess start(WorkerSpec spec)
            throws WorkflowException, InterruptedException {
        Objects.requireNonNull(spec, "spec");
        return new IsolatedWorkerProcess(spec.description(), WorkerLaunch.start(spec));
    }

    /**
     * Sends one input and waits for its verdict. A crash verdict has already closed this worker.
     *
     * @throws WorkflowException if the worker violated the protocol, which is an infrastructure
     *     failure rather than a verdict about the input
     */
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
        if (!channel.writeRequest(bytes)) {
            return crashAndClose(description + " died while accepting the input");
        }

        try {
            var result = channel.readResult(timeout);
            if (result.outcome() == StageOutcome.CRASH) {
                close();
                return new ToolResult(StageOutcome.CRASH, output.describe(result.diagnostic()));
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
                    output.describe(
                            description + " protocol failed: " + Diagnostics.message(cause)),
                    cause);
        }
    }

    /** Stops the child, releases the channel, and removes its scratch tree. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (process.isAlive()) {
            try {
                channel.requestStop();
                WorkerLaunch.awaitStop(process);
            } catch (IOException exception) {
                process.destroyForcibly();
            }
        }
        channel.close();
        output.close();
    }

    private ToolResult crashAndClose(String diagnostic) {
        close();
        return new ToolResult(StageOutcome.CRASH, output.describe(diagnostic));
    }
}
