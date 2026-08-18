package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RunShutdownHookTest {
    @Test
    void interruptsTheRunAndWaitsForItsCleanup() throws Exception {
        var installed = new CountDownLatch(1);
        var interrupted = new AtomicBoolean();
        var hook = new AtomicReference<RunShutdownHook>();
        var owner = Thread.ofPlatform().start(() -> {
            try (var current = RunShutdownHook.install()) {
                hook.set(current);
                installed.countDown();
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException exception) {
                    interrupted.set(true);
                }
            }
        });
        assertTrue(installed.await(1, TimeUnit.SECONDS));
        var requester = Thread.ofPlatform().start(hook.get()::requestShutdown);

        owner.join(1_000);
        requester.join(1_000);

        assertFalse(owner.isAlive());
        assertFalse(requester.isAlive());
        assertTrue(interrupted.get());
    }
}
