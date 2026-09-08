package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
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

    private OneShotTool() {}

    /** Drains bounded diagnostics continuously and terminates the child on timeout or interruption. */
    public static Result run(Invocation invocation) throws IOException, InterruptedException, WorkflowException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        var process = new ProcessBuilder(invocation.command()).directory(invocation.directory().toFile())
                .redirectErrorStream(true).start();
        try (var output = new BoundedOutputCapture(
                process.getInputStream(), MAXIMUM_DIAGNOSTIC_BYTES, invocation.description())) {
            process.getOutputStream().close();
            if (!process.waitFor(invocation.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new WorkflowException(invocation.description() + " timed out after "
                        + invocation.timeout() + System.lineSeparator() + output.text());
            }
            return new Result(process.exitValue(), output.text());
        } finally {
            WorkerLaunch.terminate(process);
        }
    }
}
