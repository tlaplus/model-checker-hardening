package io.github.tlaplus.hardening.workflow;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Shared stop state for independently running stages. */
final class WorkflowControl {
    enum State {
        RUNNING,
        CAPACITY_REACHED,
        FAILED
    }

    private final WorkQueue<?> queue;
    private final AtomicReference<State> state = new AtomicReference<>(State.RUNNING);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();

    WorkflowControl(WorkQueue<?> queue) {
        this.queue = Objects.requireNonNull(queue, "queue");
    }

    void capacityReached() {
        if (state.compareAndSet(State.RUNNING, State.CAPACITY_REACHED)) {
            queue.close();
        }
    }

    void fail(Throwable exception) {
        failure.compareAndSet(null, Objects.requireNonNull(exception, "exception"));
        state.set(State.FAILED);
        queue.close();
    }

    boolean shouldStopProducing() {
        return state.get() != State.RUNNING;
    }

    boolean shouldAbortParsing() {
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
}
