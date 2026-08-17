package io.github.tlaplus.hardening.workflow.execution;

import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Periodically publishes serialized workflow snapshots without delaying stage workers. */
public final class WorkflowProgressMonitor implements AutoCloseable {
    private final Duration interval;
    private final Supplier<WorkflowProgress> snapshots;
    private final Consumer<WorkflowProgress> listener;

    private volatile boolean stopping;
    private boolean reporting = true;
    private boolean closed;
    private Thread worker;

    private WorkflowProgressMonitor(
            Duration interval,
            Supplier<WorkflowProgress> snapshots,
            Consumer<WorkflowProgress> listener) {
        this.interval = Objects.requireNonNull(interval, "interval");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("progress interval must be positive");
        }
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.listener = Objects.requireNonNull(listener, "listener");
    }

    public static WorkflowProgressMonitor start(
            Duration interval,
            Supplier<WorkflowProgress> snapshots,
            Consumer<WorkflowProgress> listener) {
        var monitor = new WorkflowProgressMonitor(interval, snapshots, listener);
        if (monitor.publish()) {
            monitor.worker = Thread.ofVirtual()
                    .name("fuzztla-progress")
                    .start(monitor::run);
        }
        return monitor;
    }

    private void run() {
        while (!stopping) {
            try {
                Thread.sleep(interval);
            } catch (InterruptedException exception) {
                if (!stopping) {
                    Thread.currentThread().interrupt();
                }
                return;
            }
            if (!stopping && !publish()) {
                return;
            }
        }
    }

    private boolean publish() {
        try {
            listener.accept(snapshots.get());
            return true;
        } catch (RuntimeException exception) {
            reporting = false;
            return false;
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        stopping = true;
        if (worker != null) {
            worker.interrupt();
            var interrupted = false;
            while (worker.isAlive()) {
                try {
                    worker.join();
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (reporting) {
            publish();
        }
        closed = true;
    }
}
