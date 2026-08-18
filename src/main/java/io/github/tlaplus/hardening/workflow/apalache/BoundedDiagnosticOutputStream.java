package io.github.tlaplus.hardening.workflow.apalache;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Retains a bounded prefix while allowing tool output to continue without blocking. */
final class BoundedDiagnosticOutputStream extends OutputStream {
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private final int maximumBytes;
    private boolean truncated;

    BoundedDiagnosticOutputStream(int maximumBytes) {
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        this.maximumBytes = maximumBytes;
    }

    @Override
    public synchronized void write(int value) {
        if (captured.size() < maximumBytes) {
            captured.write(value);
        } else {
            truncated = true;
        }
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, bytes.length);
        var remaining = maximumBytes - captured.size();
        if (remaining > 0) {
            captured.write(bytes, offset, Math.min(remaining, length));
        }
        if (length > remaining) {
            truncated = true;
        }
    }

    synchronized String text() {
        var result = captured.toString(StandardCharsets.UTF_8);
        if (truncated) {
            result += System.lineSeparator() + "[Apalache output truncated]";
        }
        return result;
    }
}
