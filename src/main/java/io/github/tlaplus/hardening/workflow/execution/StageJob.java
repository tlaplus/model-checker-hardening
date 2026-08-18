package io.github.tlaplus.hardening.workflow.execution;

/** Processing of one queued item by one stage worker. */
@FunctionalInterface
public interface StageJob<T> {
    /** Whether the worker that ran a job keeps claiming queued items. */
    enum Outcome {
        CONTINUE,
        STOP
    }

    Outcome process(T item) throws Exception;
}
