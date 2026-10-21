package io.github.tlaplus.hardening.workflow.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The diagnostic of a worker that exceeds its deadline (ADR 0021). */
class WorkerTimeoutTest {
    private static final Duration STARTUP = Duration.ofSeconds(10);
    private static final Duration DEADLINE = Duration.ofSeconds(2);
    private static final String DESCRIPTION = "test worker";

    @Test
    void keepsTheToolOutputOfATimedOutInput(@TempDir Path directory) throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        ToolResult result;
        try (var worker = IsolatedWorkerProcess.start(
                new WorkerSpec(scratch, STARTUP, SlowToolWorker.class, DESCRIPTION))) {
            result = worker.request(new ToolInput("request", 0), DEADLINE);
        }

        assertEquals(StageOutcome.CRASH, result.outcome());
        assertTrue(result.diagnostic().startsWith("test worker timed out after PT2S"),
                result.diagnostic());
        assertTrue(result.diagnostic().contains("deliberate tool output"), result.diagnostic());
        try (var paths = Files.list(scratch)) {
            assertTrue(paths.findAny().isEmpty());
        }
    }

    /** Prints tool output for its input, then works past any deadline. */
    public static final class SlowToolWorker {
        private SlowToolWorker() {}

        public static void main(String[] ignoredArguments) {
            ToolWorkerRuntime.main(SlowToolWorker::run, System.err);
        }

        private static void run() throws Exception {
            try (var connection = ToolWorkerConnection.connect()) {
                ToolWorkerRuntime.serve(connection, ToolWorkerRuntime.Lifetime.ONE_INPUT, source -> {
                    var diagnostics = ToolWorkerRuntime.diagnosticBuffer("Test tool");
                    diagnostics.write("deliberate tool output".getBytes(StandardCharsets.UTF_8));
                    Thread.sleep(Duration.ofMinutes(1));
                    return new ToolResult(StageOutcome.PASS, diagnostics.text());
                });
            }
        }
    }
}
