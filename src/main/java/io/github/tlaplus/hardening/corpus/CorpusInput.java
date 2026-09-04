package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.gen.InputKind;
import java.util.Arrays;
import java.util.Objects;

/** The required fields of one encoded corpus input. */
public record CorpusInput(InputKind kind, byte[] input) {
    public CorpusInput {
        Objects.requireNonNull(kind, "kind");
        input = Objects.requireNonNull(input, "input").clone();
    }

    /** Returns an independent copy of the generator input bytes. */
    @Override
    public byte[] input() {
        return input.clone();
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof CorpusInput that
                        && kind == that.kind
                        && Arrays.equals(input, that.input);
    }

    @Override
    public int hashCode() {
        return 31 * kind.hashCode() + Arrays.hashCode(input);
    }

    /** Returns the kind the {@code kind} field names, rejecting a name the format does not define. */
    static InputKind kindFromEncodedName(String encodedName) throws CorpusFormatException {
        return InputKind.fromEncodedName(encodedName)
                .orElseThrow(() -> new CorpusFormatException(
                        "unknown input kind: " + encodedName));
    }
}
