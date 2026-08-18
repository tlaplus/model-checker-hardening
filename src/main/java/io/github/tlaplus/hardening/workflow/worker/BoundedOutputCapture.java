package io.github.tlaplus.hardening.workflow.worker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Continuously drains a process stream while retaining only a bounded prefix. */
public final class BoundedOutputCapture implements AutoCloseable {
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(1);

    private final InputStream input;
    private final int maximumBytes;
    private final String truncationLabel;
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private final Thread reader;
    private boolean truncated;

    public BoundedOutputCapture(InputStream input, int maximumBytes) {
        this(input, maximumBytes, "output");
    }

    public BoundedOutputCapture(InputStream input, int maximumBytes, String truncationLabel) {
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        this.input = java.util.Objects.requireNonNull(input, "input");
        this.maximumBytes = maximumBytes;
        this.truncationLabel = java.util.Objects.requireNonNull(truncationLabel, "truncationLabel");
        reader = Thread.startVirtualThread(this::read);
    }

    private void read() {
        var buffer = new byte[8192];
        try (input) {
            int length;
            while ((length = input.read(buffer)) >= 0) {
                append(buffer, length);
            }
        } catch (IOException ignored) {
            // Closing or terminating the child process also closes its stream.
        }
    }

    private synchronized void append(byte[] bytes, int length) {
        var remaining = maximumBytes - captured.size();
        if (remaining > 0) {
            captured.write(bytes, 0, Math.min(remaining, length));
        }
        if (length > remaining) {
            truncated = true;
        }
    }

    public synchronized String text() {
        var result = captured.toString(StandardCharsets.UTF_8);
        if (truncated) {
            result += System.lineSeparator() + "[" + truncationLabel + " truncated]";
        }
        return result;
    }

    @Override
    public void close() {
        var interrupted = false;
        try {
            reader.join(DRAIN_TIMEOUT.toMillis());
            if (reader.isAlive()) {
                try {
                    input.close();
                } catch (IOException ignored) {
                    // The child process may already have closed the pipe.
                }
                reader.join(DRAIN_TIMEOUT.toMillis());
            }
        } catch (InterruptedException exception) {
            interrupted = true;
            try {
                input.close();
            } catch (IOException ignored) {
                // The child process may already have closed the pipe.
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
