package io.github.tlaplus.hardening.workflow.worker;

import java.io.PrintStream;
import java.util.Objects;

/**
 * The part of a child worker that is the same for every tool: report an escaped failure by exiting
 * non-zero, announce itself to the parent, and serve inputs until the parent stops asking.
 *
 * <p>Each worker main keeps its own setup, because what a tool needs before its first input differs
 * — a module resolver, a fixed configuration file, a redirected Scala console. It calls {@link
 * #serve} once that setup is done.
 */
public final class ToolWorkerRuntime {
    private ToolWorkerRuntime() {}

    /** How many inputs one worker process serves. */
    public enum Lifetime {
        /** The worker exits after one input, so no tool state can leak between inputs. */
        ONE_INPUT,
        /** The worker serves inputs until the parent stops asking or the tool crashes. */
        UNTIL_CRASH
    }

    /** Everything a worker main does, including the setup its tool needs. */
    @FunctionalInterface
    public interface Body {
        void run() throws Exception;
    }

    /** Produces the verdict for one input. */
    @FunctionalInterface
    public interface Handler {
        ToolResult handle(String source) throws Exception;
    }

    /**
     * Runs a worker main. A failure that escapes the body is printed to {@code processError} and
     * exits non-zero, which the parent reports as a crash together with this output.
     */
    public static void main(Body body, PrintStream processError) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(processError, "processError");
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
            if (lifetime == Lifetime.ONE_INPUT || result.outcome() == StageOutcome.CRASH) {
                return;
            }
        }
    }
}
