package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.common.ThrowingConsumer;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Claims queued items on behalf of one stage worker under the shared CPU budget. The loop reserves
 * permits before each job and releases them afterwards, and times only the job itself, so queue,
 * capacity, and budget waits stay out of a stage's elapsed time.
 *
 * <p>One instance is shared by all workers of a stage; it holds no per-worker state.
 */
public final class StageJobLoop<T> {
    private final WorkQueue<T> queue;
    private final CpuBudget cpuBudget;
    private final CpuBudget.Priority priority;
    private final int permits;
    private final StageCounters counters;
    private final WorkflowControl control;
    private final ToIntFunction<T> generationOf;

    /**
     * @param generationOf the generation each item belongs to, which decides the order requests are
     *     served in; a stage whose work must not wait behind another generation reports
     *     {@link CpuBudget#UNORDERED_GENERATION}
     */
    public StageJobLoop(
            WorkQueue<T> queue,
            CpuBudget cpuBudget,
            CpuBudget.Priority priority,
            int permits,
            StageCounters counters,
            WorkflowControl control,
            ToIntFunction<T> generationOf) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.cpuBudget = Objects.requireNonNull(cpuBudget, "cpuBudget");
        this.priority = Objects.requireNonNull(priority, "priority");
        Preconditions.requirePositive(permits, "permits");
        this.permits = permits;
        this.counters = Objects.requireNonNull(counters, "counters");
        this.control = Objects.requireNonNull(control, "control");
        this.generationOf = Objects.requireNonNull(generationOf, "generationOf");
    }

    /**
     * Runs jobs until the queue closes and drains, or the workflow stops.
     *
     * @param job processing of one queued item. A job that has to stop its stage reports it to
     *     {@link WorkflowControl}; this loop observes that shared state and stops claiming work.
     */
    public <E extends Throwable> void run(ThrowingConsumer<T, E> job)
            throws E, InterruptedException {
        Objects.requireNonNull(job, "job");
        while (!control.shouldStop()) {
            var item = queue.take();
            if (item == null) {
                return;
            }
            if (!cpuBudget.acquire(priority, generationOf.applyAsInt(item), permits, control::shouldStop)) {
                return;
            }
            counters.elapsed().start();
            try {
                job.accept(item);
            } finally {
                counters.elapsed().stop();
                cpuBudget.release(permits);
            }
        }
    }
}
