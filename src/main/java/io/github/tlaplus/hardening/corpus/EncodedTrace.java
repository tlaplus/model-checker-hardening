package io.github.tlaplus.hardening.corpus;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;

/**
 * The counterexample a checker stage recorded (ADR 0034 §5), stored under {@code
 * stages.<checker>.trace}. The corpus keeps the bytes and does not interpret them; the workflow
 * encodes and decodes them ({@code gen.trace.TracePayload}).
 */
public record EncodedTrace(byte[] bytes) {
    public EncodedTrace {
        bytes = Objects.requireNonNull(bytes, "bytes").clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof EncodedTrace trace && Arrays.equals(bytes, trace.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "EncodedTrace[" + HexFormat.of().formatHex(bytes) + "]";
    }
}
