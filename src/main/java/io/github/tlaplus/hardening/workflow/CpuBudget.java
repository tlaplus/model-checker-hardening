package io.github.tlaplus.hardening.workflow;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/** Fair logical CPU budget shared by all stage workers. */
final class CpuBudget {
    private final Semaphore permits;

    CpuBudget(int maximumCpus) {
        if (maximumCpus <= 0) {
            throw new IllegalArgumentException("maximumCpus must be positive");
        }
        permits = new Semaphore(maximumCpus, true);
    }

    boolean acquire(BooleanSupplier cancelled) throws InterruptedException {
        while (!cancelled.getAsBoolean()) {
            if (permits.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return false;
    }

    void release() {
        permits.release();
    }
}
