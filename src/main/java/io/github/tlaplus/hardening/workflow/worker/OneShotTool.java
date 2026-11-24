package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

/** A finite CLI invocation, unlike protocol workers which serve a sequence of per-input requests. */
public final class OneShotTool {
    /** Diagnostics kept from a failing tool. Large enough for a stack trace and its causes. */
    private static final int MAXIMUM_DIAGNOSTIC_BYTES = 1024 * 1024;

    /** @param description names the invocation in a timeout diagnostic */
    public record Invocation(List<String> command, Path directory, Duration timeout, String description) {
        public Invocation {
            command = List.copyOf(command);
            if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("positive timeout required");
        }
    }
    public record Result(int exitCode, String diagnostics) {}

    /**
     * What an invocation that may outlive its timeout did: its exit status, absent when it was
     * stopped on timeout, and the output it wrote either way.
     */
    public record Attempt(OptionalInt exitCode, String diagnostics) {
        public boolean timedOut() {
            return exitCode.isEmpty();
        }
    }

    private OneShotTool() {}

    /** Runs {@code invocation} and reports a timeout as a {@link WorkflowException}. */
    public static Result run(Invocation invocation) throws IOException, InterruptedException, WorkflowException {
        var attempt = attempt(invocation);
        if (attempt.timedOut()) {
            throw new WorkflowException(timeoutMessage(invocation) + System.lineSeparator() + attempt.diagnostics());
        }
        return new Result(attempt.exitCode().getAsInt(), attempt.diagnostics());
    }

    /**
     * Runs {@code invocation} and reports a timeout in the result. Drains bounded diagnostics
     * continuously and terminates the child on timeout or interruption.
     */
    public static Attempt attempt(Invocation invocation) throws IOException, InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        var process = new ProcessBuilder(invocation.command()).directory(invocation.directory().toFile())
                .redirectErrorStream(true).start();
        try {
            var output = new BoundedOutputCapture(
                    process.getInputStream(), MAXIMUM_DIAGNOSTIC_BYTES, invocation.description());
            OptionalInt exitCode;
            // Closing the capture drains the output the child wrote before it ended, so the text is
            // read after it; a child stopped on timeout is terminated first, which closes its pipe.
            try (output) {
                process.getOutputStream().close();
                if (process.waitFor(invocation.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                    exitCode = OptionalInt.of(process.exitValue());
                } else {
                    exitCode = OptionalInt.empty();
                    WorkerLaunch.terminate(process);
                }
            }
            return new Attempt(exitCode, output.text());
        } finally {
            WorkerLaunch.terminate(process);
        }
    }

    /** Returns the diagnostic of an invocation stopped on timeout, as the JVM workers word it (ADR 0021). */
    public static String timeoutMessage(Invocation invocation) {
        return invocation.description() + " timed out after " + invocation.timeout();
    }
}
