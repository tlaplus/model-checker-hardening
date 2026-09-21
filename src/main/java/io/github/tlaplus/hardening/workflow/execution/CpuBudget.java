package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.common.Preconditions;
import java.util.Comparator;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * Priority-aware logical CPU budget shared by all stage workers.
 *
 * <p>Pending requests are served oldest generation first, then downstream-first stage priority,
 * then FIFO (ADR 0010). If the first waiting request cannot yet be satisfied, available permits are
 * reserved for it instead of being granted to later work. This prevents a multi-permit checker
 * request from being starved by smaller requests.
 */
public final class CpuBudget {
    /** Workflow priorities, from the most downstream work to the most upstream work. */
    public enum Priority {
        AGGREGATOR,
        CHECKER,
        PARSER,
        GENERATOR
    }

    /**
     * The generation a stage reports when its work must not wait behind any other generation's.
     * The aggregator uses it: aggregating an entry releases the checker capacity every generation
     * needs, so holding it behind older upstream work would stall the checkers.
     */
    public static final int UNORDERED_GENERATION = 0;

    private static final long CANCELLATION_POLL_MILLISECONDS = 100;

    private final int maximumCpus;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final PriorityQueue<Request> requests = new PriorityQueue<>(Comparator
            .comparingInt((Request request) -> request.generation)
            .thenComparing(request -> request.priority)
            .thenComparingLong(request -> request.sequence));

    private int availablePermits;
    private long nextSequence;

    public CpuBudget(int maximumCpus) {
        Preconditions.requirePositive(maximumCpus, "maximumCpus");
        this.maximumCpus = maximumCpus;
        availablePermits = maximumCpus;
    }

    /**
     * Acquires permits behind every waiter of an older generation, and behind every waiter of the
     * same generation at a more downstream stage. Returns {@code false} when {@code cancelled}
     * became true before the permits were granted; a caller that gets {@code true} must
     * {@link #release} them.
     */
    public boolean acquire(
            Priority priority, int generation, int requestedPermits, BooleanSupplier cancelled)
            throws InterruptedException {
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(cancelled, "cancelled");
        Preconditions.requireNonnegative(generation, "generation");
        Preconditions.require(requestedPermits > 0 && requestedPermits <= maximumCpus,
                "requestedPermits must be in the range 1.." + maximumCpus);

        Request request = null;
        var enqueued = false;
        lock.lockInterruptibly();
        try {
            if (cancelled.getAsBoolean()) {
                return false;
            }
            request = new Request(generation, priority, nextSequence++, requestedPermits);
            requests.add(request);
            enqueued = true;

            while (!cancelled.getAsBoolean()) {
                // The sequence number makes every request distinct, so identity and equality agree.
                if (requests.peek() == request && requestedPermits <= availablePermits) {
                    requests.remove();
                    availablePermits -= requestedPermits;
                    enqueued = false;
                    changed.signalAll();
                    return true;
                }
                changed.await(CANCELLATION_POLL_MILLISECONDS, TimeUnit.MILLISECONDS);
            }
            return false;
        } finally {
            if (enqueued) {
                requests.remove(request);
                changed.signalAll();
            }
            lock.unlock();
        }
    }

    public void release(int releasedPermits) {
        Preconditions.requirePositive(releasedPermits, "releasedPermits");

        lock.lock();
        try {
            if (releasedPermits > maximumCpus - availablePermits) {
                throw new IllegalStateException("releasedPermits exceed acquired permits");
            }
            availablePermits += releasedPermits;
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private record Request(int generation, Priority priority, long sequence, int permits) {}
}
