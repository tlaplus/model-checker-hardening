package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.common.ThrowingRunnable;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The part of a child worker that is the same for every tool: report an escaped failure by exiting
 * non-zero, announce itself to the parent, serve inputs until the parent stops asking, write each
 * input where the tool reads it, and keep a bounded record of what the tool printed.
 *
 * <p>Each worker main keeps its own setup, because what a tool needs before its first input differs
 * — a module resolver, a fixed configuration file, a redirected Scala console. It calls {@link
 * #serve} once that setup is done.
 */
public final class ToolWorkerRuntime {
    /** Tool output kept for one input, leaving room in a result frame for the truncation marker. */
    private static final int MAXIMUM_DIAGNOSTIC_BYTES = ToolWorkerProtocol.MAXIMUM_DIAGNOSTIC_BYTES - 128;

    /**
     * The output buffer of the input in progress, until {@link #serve} has written its verdict. A
     * worker is one process, so this is per-process state, read by the shutdown hook of {@link
     * #main}.
     */
    private static final AtomicReference<BoundedTextOutputStream> PENDING_OUTPUT =
            new AtomicReference<>();

    private ToolWorkerRuntime() {}

    /** How many inputs one worker process serves. */
    public enum Lifetime {
        /** The worker exits after one input, so no tool state can leak between inputs. */
        ONE_INPUT,
        /** The worker serves inputs until the parent stops asking or the tool crashes. */
        UNTIL_CRASH
    }

    /** Produces the verdict for one input. */
    @FunctionalInterface
    public interface Handler {
        ToolResult handle(ToolInput source) throws Exception;
    }

    /**
     * Runs a worker main. A failure that escapes the body is printed to {@code processError} and
     * exits non-zero, which the parent reports as a crash together with this output.
     *
     * <p>A tool may also end the JVM itself, as TLC does with {@code System.exit(1)} when its
     * liveness checker fails (tlc-019), and the parent ends a worker that exceeds its deadline with
     * {@code SIGTERM}. The verdict and the tool output it would carry are then never written, so a
     * shutdown hook prints, for the input in progress, the stacks of the tool's working threads and
     * the tool output to {@code processError}, which the parent appends to the crash verdict (ADR
     * 0021). The stacks come first, so that a long output cannot push them out of the parent's
     * bounded capture.
     *
     * @param body everything a worker main does, including the setup its tool needs
     */
    public static void main(ThrowingRunnable<Exception> body, PrintStream processError) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(processError, "processError");
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> printPendingInput(processError), "pending tool output"));
        try {
            body.run();
        } catch (Exception | StackOverflowError exception) {
            exception.printStackTrace(processError);
            System.exit(1);
        }
    }

    /**
     * Announces this worker to the parent and serves inputs. Returns when the parent stops asking,
     * after one input for a {@link Lifetime#ONE_INPUT} worker, or after a crash verdict, which
     * retires the worker.
     */
    public static void serve(
            ToolWorkerConnection connection, Lifetime lifetime, Handler handler)
            throws Exception {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(lifetime, "lifetime");
        Objects.requireNonNull(handler, "handler");
        var output = connection.output();
        var input = connection.input();
        ToolWorkerProtocol.writeHandshake(output);
        while (true) {
            var source = ToolWorkerProtocol.readRequest(input);
            if (source == null) {
                return;
            }
            var result = handler.handle(source);
            ToolWorkerProtocol.writeResult(output, result);
            PENDING_OUTPUT.set(null);
            if (lifetime == Lifetime.ONE_INPUT || result.outcome() == StageOutcome.CRASH) {
                return;
            }
        }
    }

    /** Writes the rendered specification of one input, replacing the previous input's file. */
    public static void writeInput(Path specification, ToolInput source) throws IOException {
        Files.writeString(
                specification,
                source.text(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    /**
     * Returns a buffer that keeps what a tool prints for one input, up to what one result frame
     * carries, and marks a truncation with {@code tool}'s name. The buffer is the pending output
     * of the input in progress until {@link #serve} writes that input's verdict.
     */
    public static BoundedTextOutputStream diagnosticBuffer(String tool) {
        var buffer = new BoundedTextOutputStream(MAXIMUM_DIAGNOSTIC_BYTES, tool + " output");
        PENDING_OUTPUT.set(buffer);
        return buffer;
    }

    private static void printPendingInput(PrintStream processError) {
        var pending = PENDING_OUTPUT.getAndSet(null);
        if (pending != null) {
            processError.print(ToolThreadStacks.sampleOthers());
            processError.print(pending.text());
            processError.flush();
        }
    }
}
