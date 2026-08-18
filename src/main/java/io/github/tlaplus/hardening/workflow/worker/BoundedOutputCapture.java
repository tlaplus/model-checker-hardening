package io.github.tlaplus.hardening.workflow.worker;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Objects;

/** Continuously drains a process stream while retaining only a bounded prefix. */
public final class BoundedOutputCapture implements AutoCloseable {
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(1);

    private final InputStream input;
    private final BoundedTextOutputStream output;
    private final Thread reader;

    public BoundedOutputCapture(InputStream input, int maximumBytes, String truncationLabel) {
        this.input = Objects.requireNonNull(input, "input");
        output = new BoundedTextOutputStream(maximumBytes, truncationLabel);
        reader = Thread.startVirtualThread(this::read);
    }

    private void read() {
        try (input) {
            input.transferTo(output);
        } catch (IOException ignored) {
            // Closing or terminating the child process also closes its stream.
        }
    }

    public String text() {
        return output.text();
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
