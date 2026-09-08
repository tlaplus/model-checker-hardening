package io.github.tlaplus.hardening.workflow.worker;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OneShotToolTest {
    private static OneShotTool.Invocation invocation(Path directory, String mode, Duration timeout) {
        var command = new ArrayList<String>();
        command.add(JavaLaunch.executable());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(Fixture.class.getName());
        command.add(mode);
        command.add(directory.toString());
        return new OneShotTool.Invocation(command, directory, timeout, "test fixture");
    }

    @Test
    void boundsDiagnosticsWithoutBlockingAndPreservesExitCode(@TempDir Path directory) throws Exception {
        var result = OneShotTool.run(invocation(directory, "output", Duration.ofSeconds(10)));
        // A child that writes far more than the bound must neither block nor lose its exit code;
        // the prefix contract itself is covered by BoundedTextOutputStreamTest.
        assertEquals(7, result.exitCode());
        assertTrue(result.diagnostics().contains("truncated"));
    }

    @Test
    void timeoutKillsTheChild(@TempDir Path directory) throws Exception {
        var failure = assertThrows(WorkflowException.class,
                () -> OneShotTool.run(invocation(directory, "hang", Duration.ofSeconds(2))));
        assertTrue(failure.getMessage().startsWith("test fixture timed out"), failure.getMessage());
        assertDead(directory);
    }

    @Test
    void interruptionSignalsCancellationAndKillsTheChild(@TempDir Path directory) throws Exception {
        var outcome = new AtomicReference<Throwable>();
        try (var watcher = directory.getFileSystem().newWatchService()) {
            directory.register(watcher, StandardWatchEventKinds.ENTRY_CREATE);
            var caller = Thread.ofPlatform().start(() -> {
                try { OneShotTool.run(invocation(directory, "hang", Duration.ofMinutes(1))); }
                catch (Throwable exception) { outcome.set(exception); }
            });
            try {
                boolean ready = false;
                while (!ready) {
                    var event = watcher.poll(10, java.util.concurrent.TimeUnit.SECONDS);
                    assertNotNull(event, "child did not start");
                    ready = event.pollEvents().stream().anyMatch(change -> change.context().toString().equals("ready"));
                    event.reset();
                }
                caller.interrupt();
                caller.join(Duration.ofSeconds(10));
                assertFalse(caller.isAlive());
                assertInstanceOf(InterruptedException.class, outcome.get());
                assertDead(directory);
            } finally { caller.interrupt(); }
        }
    }

    private static void assertDead(Path directory) throws Exception {
        long pid = Long.parseLong(Files.readString(directory.resolve("pid")));
        assertFalse(ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }

    public static final class Fixture {
        public static void main(String[] args) throws Exception {
            if (args[0].equals("output")) {
                System.out.print("x".repeat(2 * 1024 * 1024));
                System.exit(7);
            }
            Files.writeString(Path.of(args[1], "pid"), Long.toString(ProcessHandle.current().pid()));
            Files.createFile(Path.of(args[1], "ready"));
            new CountDownLatch(1).await();
        }
    }
}
