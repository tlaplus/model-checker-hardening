package io.github.tlaplus.hardening.workflow;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Fair logical CPU budget shared by all stage workers. */
final class CpuBudget {
    private final int maximumCpus;
    private final Semaphore permits;

    CpuBudget(int maximumCpus) {
        if (maximumCpus <= 0) {
            throw new IllegalArgumentException("maximumCpus must be positive");
        }
        this.maximumCpus = maximumCpus;
        permits = new Semaphore(maximumCpus, true);
    }

    boolean acquire(int requestedPermits, BooleanSupplier cancelled)
            throws InterruptedException {
        if (requestedPermits <= 0 || requestedPermits > maximumCpus) {
            throw new IllegalArgumentException(
                    "requestedPermits must be in the range 1.." + maximumCpus);
        }
        while (!cancelled.getAsBoolean()) {
            if (permits.tryAcquire(requestedPermits, 100, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return false;
    }

    void release(int releasedPermits) {
        if (releasedPermits <= 0) {
            throw new IllegalArgumentException("releasedPermits must be positive");
        }
        permits.release(releasedPermits);
    }
}
