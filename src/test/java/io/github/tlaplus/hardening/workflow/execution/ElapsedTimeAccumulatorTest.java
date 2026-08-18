package io.github.tlaplus.hardening.workflow.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ElapsedTimeAccumulatorTest {
    @Test
    void sumsCompletedAndActiveWorkerIntervals() throws Exception {
        var clock = new AtomicLong();
        var elapsed = new ElapsedTimeAccumulator(Duration.ofNanos(5), clock::get);
        var started = new CountDownLatch(2);
        var stopFirst = new CountDownLatch(1);
        var stopSecond = new CountDownLatch(1);
        var first = worker(elapsed, started, stopFirst);
        var second = worker(elapsed, started, stopSecond);

        first.start();
        second.start();
        started.await();
        clock.set(10);
        assertEquals(Duration.ofNanos(25), elapsed.elapsed());

        clock.set(15);
        stopFirst.countDown();
        first.join();
        clock.set(20);
        assertEquals(Duration.ofNanos(40), elapsed.elapsed());

        stopSecond.countDown();
        second.join();
        assertEquals(Duration.ofNanos(40), elapsed.elapsed());
    }

    @Test
    void requiresBalancedIntervalsAndAMonotonicClock() {
        var clock = new AtomicLong(10);
        var elapsed = new ElapsedTimeAccumulator(Duration.ZERO, clock::get);

        assertThrows(IllegalStateException.class, elapsed::stop);
        elapsed.start();
        assertThrows(IllegalStateException.class, elapsed::start);
        clock.set(9);
        assertThrows(IllegalStateException.class, elapsed::elapsed);
        clock.set(11);
        elapsed.stop();
        assertEquals(Duration.ofNanos(1), elapsed.elapsed());
    }

    private static Thread worker(
            ElapsedTimeAccumulator elapsed, CountDownLatch started, CountDownLatch stop) {
        return Thread.ofPlatform().unstarted(() -> {
            elapsed.start();
            started.countDown();
            try {
                stop.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            } finally {
                elapsed.stop();
            }
        });
    }
}
