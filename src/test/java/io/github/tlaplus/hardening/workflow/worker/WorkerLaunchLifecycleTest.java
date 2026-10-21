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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
    void givesATimedOutChildItsGracePeriodBeforeKillingIt() {
        var exits = new RecordingProcess(true, List.of(Wait.EXITED));
        WorkerLaunch.terminate(exits, WorkerLaunch.TIMED_OUT_GRACE);
        assertEquals(List.of("alive", "destroy", "wait 2000"), exits.events);

        var hangs = new RecordingProcess(true, List.of(Wait.RUNNING, Wait.EXITED));
        WorkerLaunch.terminate(hangs, WorkerLaunch.TIMED_OUT_GRACE);
        assertEquals(List.of("alive", "destroy", "wait 2000", "force", "wait"), hangs.events);
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

    /** Records the SIGTERM a termination sends through the handle. */
    private record RecordingHandle(List<String> events) implements ProcessHandle {
        @Override public boolean destroy() { events.add("destroy"); return true; }
        @Override public long pid() { throw new AssertionError("pid"); }
        @Override public Optional<ProcessHandle> parent() { throw new AssertionError("parent"); }
        @Override public Stream<ProcessHandle> children() { throw new AssertionError("children"); }
        @Override public Stream<ProcessHandle> descendants() { throw new AssertionError("descendants"); }
        @Override public Info info() { throw new AssertionError("info"); }
        @Override public CompletableFuture<ProcessHandle> onExit() { throw new AssertionError("onExit"); }
        @Override public boolean supportsNormalTermination() { return true; }
        @Override public boolean destroyForcibly() { throw new AssertionError("forced through the handle"); }
        @Override public boolean isAlive() { throw new AssertionError("alive through the handle"); }
        @Override public int compareTo(ProcessHandle other) { throw new AssertionError("compareTo"); }
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
        @Override public void destroy() { throw new AssertionError("destroy closes the pipes"); }
        @Override public ProcessHandle toHandle() { return new RecordingHandle(events); }
        @Override public Process destroyForcibly() { events.add("force"); return this; }
        @Override public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
            var millis = unit.toMillis(timeout);
            events.add(millis == 500 ? "wait" : "wait " + millis);
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
