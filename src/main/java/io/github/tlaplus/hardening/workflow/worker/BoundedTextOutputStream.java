package io.github.tlaplus.hardening.workflow.worker;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Retains a bounded UTF-8 byte prefix while discarding subsequent output. */
public final class BoundedTextOutputStream extends OutputStream {
    private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
    private final int maximumBytes;
    private final String truncationLabel;
    private boolean truncated;

    public BoundedTextOutputStream(int maximumBytes, String truncationLabel) {
        if (maximumBytes <= 0) {
            throw new IllegalArgumentException("maximumBytes must be positive");
        }
        this.maximumBytes = maximumBytes;
        this.truncationLabel = Objects.requireNonNull(truncationLabel, "truncationLabel");
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

    public synchronized String text() {
        var result = captured.toString(StandardCharsets.UTF_8);
        if (truncated) {
            result += System.lineSeparator() + "[" + truncationLabel + " truncated]";
        }
        return result;
    }
}
