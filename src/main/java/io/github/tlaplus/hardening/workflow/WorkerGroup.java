package io.github.tlaplus.hardening.workflow;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/** Owns the common lifecycle of a fixed group of stage worker threads. */
final class WorkerGroup implements AutoCloseable {
    private enum State {
        NEW,
        STARTED,
        CLOSED
    }

    private static final Runnable NO_OP = () -> {};

    private final String threadNamePrefix;

    private ExecutorService executor;
    private State state = State.NEW;

    WorkerGroup(String threadNamePrefix) {
        this.threadNamePrefix = Objects.requireNonNull(threadNamePrefix, "threadNamePrefix");
        if (threadNamePrefix.isBlank()) {
            throw new IllegalArgumentException("threadNamePrefix must not be blank");
        }
    }

    void start(int workerCount, IntFunction<? extends Runnable> workerFactory) {
        start(workerCount, workerFactory, NO_OP);
    }

    synchronized void start(
            int workerCount,
            IntFunction<? extends Runnable> workerFactory,
            Runnable afterCompletion) {
        switch (state) {
            case NEW -> {
                // Continue with worker creation.
            }
            case STARTED -> throw new IllegalStateException("worker group has already started");
            case CLOSED -> throw new IllegalStateException("worker group has already closed");
        }
        if (workerCount < 0) {
            throw new IllegalArgumentException("workerCount must be nonnegative");
        }
        Objects.requireNonNull(workerFactory, "workerFactory");
        Objects.requireNonNull(afterCompletion, "afterCompletion");

        var remaining = new AtomicInteger(workerCount);
        var prepared = new ArrayList<Runnable>(workerCount);
        for (var workerId = 0; workerId < workerCount; workerId++) {
            var task = Objects.requireNonNull(
                    workerFactory.apply(workerId), "workerFactory returned null");
            prepared.add(() -> {
                try {
                    task.run();
                } finally {
                    completeOneWorker(remaining, afterCompletion);
                }
            });
        }

        var current = Executors.newThreadPerTaskExecutor(
                Thread.ofPlatform().name(threadNamePrefix, 0).factory());
        executor = current;
        state = State.STARTED;
        var submittedWorkers = 0;
        try {
            for (var worker : prepared) {
                current.execute(worker);
                submittedWorkers++;
            }
            current.shutdown();
            if (workerCount == 0) {
                afterCompletion.run();
            }
        } catch (RuntimeException | Error failure) {
            for (var index = submittedWorkers; index < workerCount; index++) {
                try {
                    completeOneWorker(remaining, afterCompletion);
                } catch (RuntimeException | Error completionFailure) {
                    failure.addSuppressed(completionFailure);
                }
            }
            state = State.CLOSED;
            current.shutdownNow();
            try {
                current.close();
            } catch (RuntimeException | Error closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    void await() throws InterruptedException {
        ExecutorService current;
        synchronized (this) {
            if (state != State.STARTED) {
                throw new IllegalStateException("worker group has not started");
            }
            current = executor;
        }
        while (!current.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
            // Continue waiting if the effectively unbounded timeout expires.
        }
    }

    @Override
    public void close() {
        final ExecutorService current;
        synchronized (this) {
            state = State.CLOSED;
            current = executor;
        }
        if (current != null) {
            current.shutdownNow();
            current.close();
        }
    }

    private static void completeOneWorker(
            AtomicInteger remaining, Runnable afterCompletion) {
        if (remaining.decrementAndGet() == 0) {
            afterCompletion.run();
        }
    }
}
