package io.github.tlaplus.hardening.workflow.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IsolatedWorkerProcessTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String DESCRIPTION = "test worker";

    @Test
    void includesWorkerStderrInStartupFailures(@TempDir Path directory)
            throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        var failure = assertThrows(
                WorkflowException.class,
                () -> IsolatedWorkerProcess.start(
                        scratch,
                        TIMEOUT,
                        StartupFailureWorker.class,
                        List.of(),
                        DESCRIPTION));

        assertTrue(failure.getMessage().contains("test worker failed during startup"));
        assertTrue(failure.getMessage().contains("deliberate startup failure"));
        assertScratchIsEmpty(scratch);
    }

    @Test
    void includesWorkerStderrWhenItExitsDuringARequest(@TempDir Path directory)
            throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        ToolResult result;
        try (var worker = IsolatedWorkerProcess.start(
                scratch,
                TIMEOUT,
                ProcessingFailureWorker.class,
                List.of(),
                DESCRIPTION)) {
            result = worker.request("request", TIMEOUT);
        }

        assertEquals(StageOutcome.CRASH, result.outcome());
        assertTrue(result.diagnostic().contains("exited while processing"));
        assertTrue(result.diagnostic().contains("deliberate processing failure"));
        assertScratchIsEmpty(scratch);
    }

    private static void assertScratchIsEmpty(Path scratch) throws Exception {
        try (var paths = Files.list(scratch)) {
            assertTrue(paths.findAny().isEmpty());
        }
    }

    public static final class StartupFailureWorker {
        private StartupFailureWorker() {}

        public static void main(String[] ignoredArguments) {
            System.err.println("deliberate startup failure");
        }
    }

    public static final class ProcessingFailureWorker {
        private ProcessingFailureWorker() {}

        public static void main(String[] ignoredArguments) throws Exception {
            var output = new DataOutputStream(new BufferedOutputStream(System.out));
            output.writeInt(ToolWorkerProtocol.MAGIC);
            output.writeInt(ToolWorkerProtocol.VERSION);
            output.flush();

            var input = new DataInputStream(System.in);
            var length = input.readInt();
            input.readNBytes(length);
            System.err.println("deliberate processing failure");
        }
    }
}
