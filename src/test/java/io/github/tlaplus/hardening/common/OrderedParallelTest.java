package io.github.tlaplus.hardening.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OrderedParallelTest {
    private static final List<Integer> ITEMS = IntStream.range(0, 2_000).boxed().toList();

    @Test
    void returnsResultsInListOrderForAnyThreadCount() {
        for (var threads : List.of(1, 2, 8)) {
            // Earlier items take longer, so that they finish after later ones.
            assertEquals(ITEMS.stream().map(item -> item * 2).toList(), collect(ITEMS, threads, item -> {
                spin(item % 7 == 0 ? 20_000 : 0);
                return item * 2;
            }));
        }
    }

    @Test
    void runsOnTheCallingThreadWithOneThread() {
        var caller = Thread.currentThread();
        assertEquals(List.of(true, true), collect(List.of(1, 2), 1, item -> Thread.currentThread() == caller));
    }

    @Test
    void usesSeveralThreads() throws InterruptedException {
        var threads = ConcurrentHashMap.<Thread>newKeySet();
        var bothStarted = new CountDownLatch(2);
        collect(List.of(1, 2), 2, item -> {
            threads.add(Thread.currentThread());
            bothStarted.countDown();
            await(bothStarted);
            return item;
        });
        assertEquals(2, threads.size());
        assertFalse(threads.contains(Thread.currentThread()));
    }

    @Test
    void throwsTheFailureOfTheFirstFailingItemInListOrder() {
        var first = new IllegalStateException("first");
        var later = new IllegalArgumentException("later");
        var released = new CountDownLatch(1);
        var results = new ArrayList<Integer>();
        try (var mapped = OrderedParallel.map(List.of(0, 1, 2, 3), 4, (Integer item) -> {
            switch (item) {
                case 1 -> {
                    // Fails only after the later item has already failed.
                    await(released);
                    throw first;
                }
                case 2 -> {
                    released.countDown();
                    throw later;
                }
                default -> {
                    return item;
                }
            }
        })) {
            results.add(mapped.next());
            assertSame(first, assertThrows(IllegalStateException.class, mapped::next));
        }
        assertEquals(List.of(0), results);
    }

    @Test
    void propagatesAnError() {
        var error = new StackOverflowError();
        try (var mapped = OrderedParallel.map(List.of(0, 1), 2, (Integer item) -> {
            if (item == 1) {
                throw error;
            }
            return item;
        })) {
            assertEquals(0, mapped.next());
            assertSame(error, assertThrows(StackOverflowError.class, mapped::next));
        }
    }

    @Test
    void endsAfterTheLastItem() {
        try (var mapped = OrderedParallel.map(List.<Integer>of(), 4, Function.identity())) {
            assertFalse(mapped.hasNext());
            assertThrows(NoSuchElementException.class, mapped::next);
        }
        assertThrows(IllegalArgumentException.class, () -> OrderedParallel.map(ITEMS, 0, Function.identity()));
    }

    @Test
    void closingStopsWorkThatIsStillRunning() throws InterruptedException {
        var running = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        var mapped = OrderedParallel.map(List.of(0, 1), 2, (Integer item) -> {
            if (item == 1) {
                running.countDown();
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException exception) {
                    interrupted.countDown();
                }
            }
            return item;
        });
        assertEquals(0, mapped.next());
        assertTrue(running.await(10, TimeUnit.SECONDS));
        mapped.close();
        assertTrue(interrupted.await(10, TimeUnit.SECONDS));
    }

    private static <R> List<R> collect(List<Integer> items, int threads, Function<Integer, R> function) {
        var results = new ArrayList<R>();
        try (var mapped = OrderedParallel.map(items, threads, function)) {
            mapped.forEachRemaining(results::add);
        }
        return results;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out");
            }
        } catch (InterruptedException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void spin(int iterations) {
        var sink = 0L;
        for (var i = 0; i < iterations; i++) {
            sink += i;
        }
        if (sink == -1) {
            throw new AssertionError();
        }
    }
}
