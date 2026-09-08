package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** A finite CLI invocation, unlike protocol workers which serve a sequence of per-input requests. */
public final class OneShotTool {
    public record Invocation(List<String> command, Path directory, Duration timeout) {
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
        var output = new BoundedTextOutputStream(1024 * 1024, "tool diagnostics");
        var failure = new AtomicReference<IOException>();
        var drain = Thread.ofVirtual().name("library-tool-output").start(() -> {
            try (var input = process.getInputStream()) {
                input.transferTo(output);
            } catch (IOException exception) { failure.set(exception); }
        });
        try {
            process.getOutputStream().close();
            if (!process.waitFor(invocation.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                throw new WorkflowException("library typechecking timed out after " + invocation.timeout()
                        + System.lineSeparator() + output.text());
            }
            drain.join();
            if (failure.get() != null) throw failure.get();
            return new Result(process.exitValue(), output.text());
        } finally {
            WorkerLaunch.terminate(process);
            process.getInputStream().close();
            drain.interrupt();
        }
    }
}
