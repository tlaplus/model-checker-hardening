package io.github.tlaplus.hardening.common;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

/**
 * Applies a function to the items of a list on a pool of platform threads, and returns the results
 * in list order to the calling thread.
 *
 * <p>At most {@value #LOOKAHEAD_PER_THREAD} results per thread are computed ahead of the caller, so
 * memory stays bounded however long the list is, and a slow item does not stall the other threads.
 * With one thread, or fewer than two items, the function runs on the calling thread and no pool is
 * created. {@link #close} stops the pool; results not yet taken are discarded.
 *
 * <p>The function must be safe to call concurrently. A caller that needs checked failures should
 * return them as values: a {@link RuntimeException} or {@link Error} the function throws is
 * rethrown by {@link #next} for the item that raised it.
 */
public final class OrderedParallel<T, R> implements Iterator<R>, AutoCloseable {
    static final int LOOKAHEAD_PER_THREAD = 4;

    private final List<T> items;
    private final Function<? super T, ? extends R> function;
    private final ExecutorService executor;
    private final int lookahead;
    private final ArrayDeque<Future<? extends R>> pending = new ArrayDeque<>();
    private int submitted;
    private int taken;

    private OrderedParallel(List<T> items, int threads, Function<? super T, ? extends R> function) {
        this.items = List.copyOf(items);
        this.function = Objects.requireNonNull(function, "function");
        Preconditions.requirePositive(threads, "threads");
        var workers = Math.min(threads, this.items.size());
        executor = workers < 2
                ? null
                : Executors.newFixedThreadPool(
                        workers, Thread.ofPlatform().name("fuzztla-parallel-", 0).daemon().factory());
        lookahead = workers * LOOKAHEAD_PER_THREAD;
    }

    /** Starts applying {@code function} to {@code items} on up to {@code threads} threads. */
    public static <T, R> OrderedParallel<T, R> map(
            List<T> items, int threads, Function<? super T, ? extends R> function) {
        return new OrderedParallel<>(items, threads, function);
    }

    @Override
    public boolean hasNext() {
        return taken < items.size();
    }

    /**
     * Returns the result for the next item in list order.
     *
     * @throws CancellationException when the calling thread is interrupted while waiting; the
     *     interrupt flag is restored
     */
    @Override
    public R next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        if (executor == null) {
            return function.apply(items.get(taken++));
        }
        while (submitted < items.size() && pending.size() < lookahead) {
            var item = items.get(submitted++);
            pending.addLast(executor.submit(() -> function.apply(item)));
        }
        taken++;
        return await(pending.removeFirst());
    }

    @Override
    public void close() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    private static <R> R await(Future<? extends R> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            var cancelled = new CancellationException("interrupted while waiting for a parallel result");
            cancelled.initCause(exception);
            throw cancelled;
        } catch (ExecutionException exception) {
            switch (exception.getCause()) {
                case RuntimeException failure -> throw failure;
                case Error failure -> throw failure;
                default -> throw new IllegalStateException(exception.getCause());
            }
        }
    }
}
