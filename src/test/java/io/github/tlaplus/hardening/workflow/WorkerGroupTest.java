package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WorkerGroupTest {
    @Test
    void startsNamedWorkersAndCompletesOnce() throws Exception {
        var names = ConcurrentHashMap.<String>newKeySet();
        var completions = new AtomicInteger();
        var workers = new WorkerGroup("stage-worker-");

        workers.start(
                3,
                _ -> () -> names.add(Thread.currentThread().getName()),
                completions::incrementAndGet);
        workers.await();

        assertEquals(Set.of("stage-worker-0", "stage-worker-1", "stage-worker-2"), names);
        assertEquals(1, completions.get());
    }

    @Test
    void supportsAnEmptyGroupAndRejectsRepeatedStarts() throws Exception {
        var completions = new AtomicInteger();
        var workers = new WorkerGroup("empty-worker-");

        workers.start(0, _ -> () -> {}, completions::incrementAndGet);
        workers.await();

        assertEquals(1, completions.get());
        assertThrows(IllegalStateException.class, () -> workers.start(0, _ -> () -> {}));
    }

    @Test
    void closeBeforeStartIsTerminal() {
        var workers = new WorkerGroup("unused-worker-");

        workers.close();

        assertThrows(IllegalStateException.class, () -> workers.start(1, _ -> () -> {}));
        assertThrows(IllegalStateException.class, workers::await);
    }

    @Test
    void closeInterruptsAndJoinsWorkersWhilePreservingCallerInterrupt() throws Exception {
        var started = new CountDownLatch(1);
        var workerInterrupted = new AtomicBoolean();
        var workers = new WorkerGroup("blocking-worker-");
        workers.start(1, _ -> () -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                workerInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });
        started.await();

        Thread.currentThread().interrupt();
        try {
            workers.close();
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertTrue(workerInterrupted.get());
    }

    @Test
    void awaitPropagatesInterruptionAndCloseTerminatesWorkers() throws Exception {
        var started = new CountDownLatch(1);
        var workerInterrupted = new AtomicBoolean();
        var workers = new WorkerGroup("await-worker-");
        workers.start(1, _ -> () -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                workerInterrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });
        started.await();

        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class, workers::await);
            assertFalse(Thread.currentThread().isInterrupted());
        } finally {
            workers.close();
            Thread.interrupted();
        }
        assertTrue(workerInterrupted.get());
    }
}
