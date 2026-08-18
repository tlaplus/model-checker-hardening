package io.github.tlaplus.hardening.workflow.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
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
                new WorkerSpec(scratch, TIMEOUT, StartupFailureWorker.class, DESCRIPTION)));

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
                new WorkerSpec(scratch, TIMEOUT, ProcessingFailureWorker.class, DESCRIPTION))) {
            result = worker.request("request", TIMEOUT);
        }

        assertEquals(StageOutcome.CRASH, result.outcome());
        assertTrue(result.diagnostic().contains("exited while processing"));
        assertTrue(result.diagnostic().contains("deliberate processing failure"));
        assertScratchIsEmpty(scratch);
    }

    @Test
    void includesFatalErrorReportsWhenAWorkerExits(@TempDir Path directory)
            throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        ToolResult result;
        try (var worker = IsolatedWorkerProcess.start(
                new WorkerSpec(scratch, TIMEOUT, FatalErrorWorker.class, DESCRIPTION))) {
            result = worker.request("request", TIMEOUT);
        }

        assertEquals(StageOutcome.CRASH, result.outcome());
        assertTrue(result.diagnostic().contains("fatal error report"));
        assertTrue(result.diagnostic().contains("deliberate fatal error report"));
        assertScratchIsEmpty(scratch);
    }

    @Test
    void killsATimedOutWorkerAndAllowsAReplacement(@TempDir Path directory)
            throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        try (var worker = IsolatedWorkerProcess.start(
                new WorkerSpec(scratch, TIMEOUT, HangingWorker.class, DESCRIPTION))) {
            assertEquals(
                    StageOutcome.CRASH,
                    worker.request("request", Duration.ZERO).outcome());
        }
        try (var replacement = IsolatedWorkerProcess.start(
                new WorkerSpec(scratch, TIMEOUT, ClassifiedFailureWorker.class, DESCRIPTION))) {
            assertEquals(
                    StageOutcome.FAIL,
                    replacement.request("request", TIMEOUT).outcome());
        }
        assertScratchIsEmpty(scratch);
    }

    @Test
    void roundTripsAWorkerFailureCode(@TempDir Path directory) throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        ToolResult result;
        try (var worker = IsolatedWorkerProcess.start(
                new WorkerSpec(scratch, TIMEOUT, ClassifiedFailureWorker.class, DESCRIPTION))) {
            result = worker.request("request", TIMEOUT);
        }

        assertEquals(StageOutcome.FAIL, result.outcome());
        assertEquals(
                CheckerFailureCode.SPEC_EVAL,
                result.failureCode().orElseThrow());
        assertEquals("Error: undefined expression", result.diagnostic());
        assertScratchIsEmpty(scratch);
    }

    @Test
    void nativeStandardOutputCannotCorruptTheProtocol(@TempDir Path directory)
            throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        ToolResult result;
        try (var worker = IsolatedWorkerProcess.start(
                new WorkerSpec(scratch, TIMEOUT, NativeOutputWorker.class, DESCRIPTION))) {
            result = worker.request("request", TIMEOUT);
        }

        assertEquals(StageOutcome.PASS, result.outcome());
        assertEquals("accepted", result.diagnostic());
        assertScratchIsEmpty(scratch);
    }

    @Test
    void rejectsAnUnknownWorkerFailureCode(@TempDir Path directory) throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        try (var worker = IsolatedWorkerProcess.start(
                new WorkerSpec(scratch, TIMEOUT, UnknownFailureCodeWorker.class, DESCRIPTION))) {
            var failure = assertThrows(
                    WorkflowException.class,
                    () -> worker.request("request", TIMEOUT));
            assertTrue(failure.getMessage().contains("protocol failed"));
            assertTrue(failure.getCause().getMessage().contains("unknown failure code: 999"));
        }
        assertScratchIsEmpty(scratch);
    }

    private static void assertScratchIsEmpty(Path scratch) throws Exception {
        try (var paths = Files.list(scratch)) {
            assertTrue(paths.findAny().isEmpty());
        }
    }

    public static final class StartupFailureWorker {
        private StartupFailureWorker() {}

        public static void main(String[] ignoredArguments) throws Exception {
            try (var ignored = ToolWorkerConnection.connect()) {
                System.err.println("deliberate startup failure");
            }
        }
    }

    public static final class ProcessingFailureWorker {
        private ProcessingFailureWorker() {}

        public static void main(String[] ignoredArguments) throws Exception {
            try (var connection = ToolWorkerConnection.connect()) {
                ToolWorkerProtocol.writeHandshake(connection.output());
                ToolWorkerProtocol.readRequest(connection.input());
                System.err.println("deliberate processing failure");
            }
        }
    }

    public static final class ClassifiedFailureWorker {
        private ClassifiedFailureWorker() {}

        public static void main(String[] ignoredArguments) throws Exception {
            try (var connection = ToolWorkerConnection.connect()) {
                ToolWorkerProtocol.writeHandshake(connection.output());
                ToolWorkerProtocol.readRequest(connection.input());
                ToolWorkerProtocol.writeResult(
                        connection.output(),
                        ToolResult.failure(
                                CheckerFailureCode.SPEC_EVAL,
                                "Error: undefined expression"));
            }
        }
    }

    public static final class FatalErrorWorker {
        private FatalErrorWorker() {}

        public static void main(String[] ignoredArguments) throws Exception {
            try (var connection = ToolWorkerConnection.connect()) {
                ToolWorkerProtocol.writeHandshake(connection.output());
                ToolWorkerProtocol.readRequest(connection.input());
                Files.writeString(
                        Path.of(System.getProperty("java.io.tmpdir"), "hs_err_pid-test.log"),
                        "deliberate fatal error report");
            }
        }
    }

    public static final class HangingWorker {
        private HangingWorker() {}

        public static void main(String[] ignoredArguments) throws Exception {
            try (var connection = ToolWorkerConnection.connect()) {
                ToolWorkerProtocol.writeHandshake(connection.output());
                ToolWorkerProtocol.readRequest(connection.input());
                Thread.sleep(Duration.ofMinutes(1));
            }
        }
    }

    public static final class UnknownFailureCodeWorker {
        private UnknownFailureCodeWorker() {}

        public static void main(String[] ignoredArguments) throws Exception {
            try (var connection = ToolWorkerConnection.connect()) {
                var output = connection.output();
                ToolWorkerProtocol.writeHandshake(output);
                ToolWorkerProtocol.readRequest(connection.input());
                output.writeInt(StageOutcome.FAIL.protocolCode());
                output.writeInt(999);
                output.writeInt(0);
                output.flush();
            }
        }
    }

    public static final class NativeOutputWorker {
        private NativeOutputWorker() {}

        public static void main(String[] ignoredArguments) throws Exception {
            try (var connection = ToolWorkerConnection.connect()) {
                ToolWorkerProtocol.writeHandshake(connection.output());
                ToolWorkerProtocol.readRequest(connection.input());
                var nativeOutput = new FileOutputStream(FileDescriptor.out);
                nativeOutput.write("Term".getBytes(StandardCharsets.UTF_8));
                nativeOutput.flush();
                ToolWorkerProtocol.writeResult(
                        connection.output(),
                        new ToolResult(StageOutcome.PASS, "accepted"));
            }
        }
    }
}
