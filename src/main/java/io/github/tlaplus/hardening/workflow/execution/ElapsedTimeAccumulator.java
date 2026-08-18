package io.github.tlaplus.hardening.workflow.execution;

import java.time.Duration;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Accumulates active intervals across workers using a monotonic clock. */
public final class ElapsedTimeAccumulator {
    private final LongSupplier nanoTime;
    private final HashMap<Thread, Long> active = new HashMap<>();

    private long completedNanos;

    public ElapsedTimeAccumulator() {
        this(Duration.ZERO);
    }

    public ElapsedTimeAccumulator(Duration previous) {
        this(previous, System::nanoTime);
    }

    ElapsedTimeAccumulator(Duration previous, LongSupplier nanoTime) {
        Objects.requireNonNull(previous, "previous");
        if (previous.isNegative()) {
            throw new IllegalArgumentException("previous elapsed time must be nonnegative");
        }
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime");
        completedNanos = previous.toNanos();
    }

    /** Starts one interval for the calling worker. */
    public synchronized void start() {
        var worker = Thread.currentThread();
        if (active.putIfAbsent(worker, nanoTime.getAsLong()) != null) {
            throw new IllegalStateException("elapsed-time interval is already active");
        }
    }

    /** Stops the calling worker's active interval. */
    public synchronized void stop() {
        var startedAt = active.remove(Thread.currentThread());
        if (startedAt == null) {
            throw new IllegalStateException("elapsed-time interval is not active");
        }
        completedNanos = Math.addExact(completedNanos, elapsedSince(startedAt, nanoTime.getAsLong()));
    }

    /** Returns completed time plus the current duration of every active interval. */
    public synchronized Duration elapsed() {
        var now = nanoTime.getAsLong();
        var result = completedNanos;
        for (var startedAt : active.values()) {
            result = Math.addExact(result, elapsedSince(startedAt, now));
        }
        return Duration.ofNanos(result);
    }

    private static long elapsedSince(long startedAt, long now) {
        var elapsed = now - startedAt;
        if (elapsed < 0) {
            throw new IllegalStateException("monotonic clock moved backwards");
        }
        return elapsed;
    }
}
