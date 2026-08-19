package io.github.tlaplus.hardening.workflow.execution;

/**
 * Processing of one queued item by one stage worker.
 *
 * <p>A job that has to stop its stage reports it to {@link WorkflowControl}; the loop around the
 * job observes that shared state and stops claiming work.
 */
@FunctionalInterface
public interface StageJob<T> {
    void process(T item) throws Exception;
}
