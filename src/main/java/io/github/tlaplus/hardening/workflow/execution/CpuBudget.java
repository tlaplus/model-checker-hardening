package io.github.tlaplus.hardening.workflow.execution;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;

/**
 * Priority-aware logical CPU budget shared by all stage workers.
 *
 * <p>Pending requests are served in downstream-first order and FIFO order within one priority.
 * If the first request at the highest waiting priority cannot yet be satisfied, available permits
 * are reserved for it instead of being granted to upstream work. This prevents a multi-permit
 * checker request from being starved by smaller requests.
 */
public final class CpuBudget {
    /** Workflow priorities, from the most downstream work to the most upstream work. */
    public enum Priority {
        CHECKER,
        PARSER,
        GENERATOR
    }

    private static final long CANCELLATION_POLL_MILLISECONDS = 100;

    private final int maximumCpus;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition changed = lock.newCondition();
    private final EnumMap<Priority, ArrayDeque<Request>> requests =
            new EnumMap<>(Priority.class);

    private int availablePermits;

    public CpuBudget(int maximumCpus) {
        if (maximumCpus <= 0) {
            throw new IllegalArgumentException("maximumCpus must be positive");
        }
        this.maximumCpus = maximumCpus;
        availablePermits = maximumCpus;
        for (var priority : Priority.values()) {
            requests.put(priority, new ArrayDeque<>());
        }
    }

    public boolean acquire(
            Priority priority, int requestedPermits, BooleanSupplier cancelled)
            throws InterruptedException {
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(cancelled, "cancelled");
        if (requestedPermits <= 0 || requestedPermits > maximumCpus) {
            throw new IllegalArgumentException(
                    "requestedPermits must be in the range 1.." + maximumCpus);
        }

        var request = new Request(requestedPermits);
        var enqueued = false;
        lock.lockInterruptibly();
        try {
            if (cancelled.getAsBoolean()) {
                return false;
            }
            requests.get(priority).addLast(request);
            enqueued = true;

            while (!cancelled.getAsBoolean()) {
                if (canAcquire(priority, request)) {
                    requests.get(priority).removeFirst();
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
                requests.get(priority).remove(request);
                changed.signalAll();
            }
            lock.unlock();
        }
    }

    public void release(int releasedPermits) {
        if (releasedPermits <= 0) {
            throw new IllegalArgumentException("releasedPermits must be positive");
        }

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

    private boolean canAcquire(Priority priority, Request request) {
        if (requests.get(priority).peekFirst() != request || hasHigherPriorityRequest(priority)) {
            return false;
        }
        return request.permits <= availablePermits;
    }

    private boolean hasHigherPriorityRequest(Priority priority) {
        return switch (priority) {
            case CHECKER -> false;
            case PARSER -> !requests.get(Priority.CHECKER).isEmpty();
            case GENERATOR ->
                !requests.get(Priority.CHECKER).isEmpty()
                        || !requests.get(Priority.PARSER).isEmpty();
        };
    }

    private static final class Request {
        private final int permits;

        private Request(int permits) {
            this.permits = permits;
        }
    }
}
