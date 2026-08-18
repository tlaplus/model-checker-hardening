package io.github.tlaplus.hardening.cli;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/** Converts JVM shutdown into an interruption and waits for workflow exit persistence. */
final class RunShutdownHook implements AutoCloseable {
    private final Thread owner;
    private final Thread hook;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final CountDownLatch completed = new CountDownLatch(1);

    private RunShutdownHook(Thread owner) {
        this.owner = owner;
        hook = Thread.ofPlatform().name("fuzztla-run-shutdown").unstarted(this::requestShutdown);
        Runtime.getRuntime().addShutdownHook(hook);
    }

    static RunShutdownHook install() {
        return new RunShutdownHook(Thread.currentThread());
    }

    /** Package-visible so coordination can be tested without terminating the test JVM. */
    void requestShutdown() {
        if (!active.compareAndSet(true, false)) {
            return;
        }
        owner.interrupt();
        var interrupted = false;
        while (true) {
            try {
                completed.await();
                break;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        active.set(false);
        completed.countDown();
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException exception) {
            // Shutdown has started; the hook is already running or about to run.
        }
    }
}
