package io.github.tlaplus.hardening.workflow.execution;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounds the current occupancy of one stage's result directories across its workers. A worker
 * reserves a slot before it produces a result that would land there.
 */
public final class OccupancyGate {
    private final AtomicInteger occupancy;
    private final int maximum;

    public OccupancyGate(long initialOccupancy, long maximum) {
        if (initialOccupancy < 0) {
            throw new IllegalArgumentException("initialOccupancy must be nonnegative");
        }
        if (maximum < 0) {
            throw new IllegalArgumentException("maximum must be nonnegative");
        }
        occupancy = new AtomicInteger(Math.toIntExact(initialOccupancy));
        this.maximum = Math.toIntExact(maximum);
    }

    /** Reserves one slot, or reports that the stage's result capacity is exhausted. */
    public boolean reserve() {
        while (true) {
            var current = occupancy.get();
            if (current >= maximum) {
                return false;
            }
            if (occupancy.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }
}
