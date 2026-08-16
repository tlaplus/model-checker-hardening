package io.github.tlaplus.hardening.workflow;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Shared stop state for independently running stages. */
final class WorkflowControl {
    enum State {
        RUNNING,
        CAPACITY_REACHED,
        FAILED
    }

    private final List<WorkQueue<?>> queues;
    private final AtomicReference<State> state = new AtomicReference<>(State.RUNNING);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    WorkflowControl(WorkQueue<?>... queues) {
        Objects.requireNonNull(queues, "queues");
        this.queues = List.of(queues);
        if (this.queues.isEmpty()) {
            throw new IllegalArgumentException("at least one workflow queue is required");
        }
    }

    void capacityReached() {
        if (state.compareAndSet(State.RUNNING, State.CAPACITY_REACHED)) {
            closeQueues();
        }
    }

    void fail(Throwable exception) {
        failure.compareAndSet(null, Objects.requireNonNull(exception, "exception"));
        state.set(State.FAILED);
        closeQueues();
    }

    boolean shouldStopProducing() {
        return state.get() != State.RUNNING;
    }

    boolean shouldAbortParsing() {
        return state.get() != State.RUNNING;
    }

    boolean shouldStopChecking() {
        return state.get() != State.RUNNING;
    }

    boolean hasFailed() {
        return state.get() == State.FAILED;
    }

    State state() {
        return state.get();
    }

    Throwable failure() {
        return failure.get();
    }

    private void closeQueues() {
        queues.forEach(WorkQueue::close);
    }
}
