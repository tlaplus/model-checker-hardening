package io.github.tlaplus.hardening.workflow.worker;

import static org.junit.jupiter.api.Assertions.*;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class WorkerLaunchLifecycleTest {
    enum Wait { EXITED, RUNNING, INTERRUPTED }
    record Case(boolean graceful, boolean alive, List<Wait> waits, List<String> events) {}

    @TestFactory
    Stream<DynamicTest> preservesEveryTerminationWaitAndEscalation() {
        var cases = List.of(
                new Case(false, false, List.of(), List.of("alive")),
                new Case(false, true, List.of(Wait.EXITED), List.of("alive", "destroy", "wait")),
                new Case(false, true, List.of(Wait.RUNNING, Wait.EXITED),
                        List.of("alive", "destroy", "wait", "force", "wait")),
                new Case(false, true, List.of(Wait.INTERRUPTED), List.of("alive", "destroy", "wait", "force")),
                new Case(true, false, List.of(Wait.EXITED, Wait.EXITED), List.of("wait", "wait")),
                new Case(true, true, List.of(Wait.RUNNING, Wait.EXITED), List.of("wait", "destroy", "wait")),
                new Case(true, true, List.of(Wait.RUNNING, Wait.RUNNING, Wait.RUNNING),
                        List.of("wait", "destroy", "wait", "force", "wait")),
                new Case(true, true, List.of(Wait.INTERRUPTED), List.of("wait", "force")),
                new Case(true, true, List.of(Wait.RUNNING, Wait.INTERRUPTED),
                        List.of("wait", "destroy", "wait", "force")));
        return cases.stream().map(test -> DynamicTest.dynamicTest(test.toString(), () -> {
            assertFalse(Thread.currentThread().isInterrupted());
            var process = new RecordingProcess(test.alive(), test.waits());
            try {
                if (test.graceful()) WorkerLaunch.awaitStop(process);
                else WorkerLaunch.terminate(process);
                assertEquals(test.events(), process.events);
                assertTrue(process.waits.isEmpty());
                assertEquals(test.waits().contains(Wait.INTERRUPTED), Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }));
    }

    @Test
    void keepsTheAcceptTimeoutCauseAndDeletesAllScratch(@TempDir Path scratch) throws Exception {
        var failure = assertThrows(WorkflowException.class, () -> IsolatedWorkerProcess.start(
                new WorkerSpec(scratch, Duration.ZERO, NeverConnects.class, "timeout worker")));
        assertTrue(failure.getMessage().startsWith("timeout worker did not connect within PT0S"));
        assertInstanceOf(TimeoutException.class, failure.getCause());
        try (var files = Files.list(scratch)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void crashFactoryPreservesOutputThenTheCompleteTrace() {
        for (var failure : List.of(new Exception("escaped"), new StackOverflowError("overflow"))) {
            for (var captured : List.of("", "tool output", "tool output\n")) {
                var result = ToolResult.crash(failure, captured);
                assertEquals(StageOutcome.CRASH, result.outcome());
                assertTrue(result.failureCode().isEmpty());
                assertEquals(WorkerDiagnostics.append(captured, Diagnostics.stackTrace(failure)), result.diagnostic());
            }
        }
    }

    public static final class NeverConnects {
        public static void main(String[] ignored) throws InterruptedException {
            new CountDownLatch(1).await();
        }
    }

    private static final class RecordingProcess extends Process {
        final boolean alive;
        final ArrayDeque<Wait> waits;
        final List<String> events = new ArrayList<>();

        RecordingProcess(boolean alive, List<Wait> waits) {
            this.alive = alive;
            this.waits = new ArrayDeque<>(waits);
        }

        @Override public boolean isAlive() { events.add("alive"); return alive; }
        @Override public void destroy() { events.add("destroy"); }
        @Override public Process destroyForcibly() { events.add("force"); return this; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            assertEquals(500, unit.toMillis(timeout));
            events.add("wait");
            return switch (waits.removeFirst()) {
                case EXITED -> true;
                case RUNNING -> false;
                case INTERRUPTED -> throw new InterruptedException("injected");
            };
        }
        @Override public int waitFor() { throw new AssertionError("unbounded wait"); }
        @Override public int exitValue() { throw new AssertionError("exit value"); }
        @Override public OutputStream getOutputStream() { throw new AssertionError("stdin"); }
        @Override public InputStream getInputStream() { throw new AssertionError("stdout"); }
        @Override public InputStream getErrorStream() { throw new AssertionError("stderr"); }
    }
}
