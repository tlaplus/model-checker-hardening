package io.github.tlaplus.hardening.common;

import java.io.IOException;

/** Best-effort I/O cleanup that retains the original failure and its suppressed diagnostics. */
public final class Cleanup {
    private Cleanup() {}

    public static void suppressIOException(Throwable failure, ThrowingRunnable<IOException> cleanup) {
        try {
            cleanup.run();
        } catch (IOException exception) {
            failure.addSuppressed(exception);
        }
    }
}
