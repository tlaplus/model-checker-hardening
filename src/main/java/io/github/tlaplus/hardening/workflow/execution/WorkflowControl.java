package io.github.tlaplus.hardening.workflow.execution;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Shared stop state for independently running stages. */
public final class WorkflowControl {
    public enum State {
        RUNNING,
        CAPACITY_REACHED,
        FAILED
    }

    private final List<WorkQueue<?>> queues;
    private final AtomicReference<State> state = new AtomicReference<>(State.RUNNING);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final CopyOnWriteArrayList<Runnable> stopListeners = new CopyOnWriteArrayList<>();

    public WorkflowControl(WorkQueue<?>... queues) {
        Objects.requireNonNull(queues, "queues");
        this.queues = List.of(queues);
        if (this.queues.isEmpty()) {
            throw new IllegalArgumentException("at least one workflow queue is required");
        }
    }

    public void capacityReached() {
        if (state.compareAndSet(State.RUNNING, State.CAPACITY_REACHED)) {
            closeQueues();
            notifyStop();
        }
    }

    public void fail(Throwable exception) {
        failure.compareAndSet(null, Objects.requireNonNull(exception, "exception"));
        state.set(State.FAILED);
        closeQueues();
        notifyStop();
    }

    /** Registers a signal for a coordinator waiting on stage progress. */
    public void onStop(Runnable listener) {
        stopListeners.add(Objects.requireNonNull(listener, "listener"));
        if (shouldStop()) {
            listener.run();
        }
    }

    /** Reports whether every stage should stop claiming new work. */
    public boolean shouldStop() {
        return state.get() != State.RUNNING;
    }

    public boolean hasFailed() {
        return state.get() == State.FAILED;
    }

    public State state() {
        return state.get();
    }

    public Throwable failure() {
        return failure.get();
    }

    private void closeQueues() {
        queues.forEach(WorkQueue::close);
    }

    private void notifyStop() {
        stopListeners.forEach(Runnable::run);
    }
}
