package io.github.tlaplus.hardening.gen;

import java.util.Objects;
import java.util.Optional;

/**
 * What a generator payload decodes to.
 *
 * <p>This lives beside the generators rather than beside the corpus because it names a decoder,
 * not a storage concern: the configuration selects which decoder a run produces, the corpus
 * records the answer in its {@code kind} field, and every stage regenerates through the decoder
 * the entry names. One enum keyed by that concept keeps those three from drifting apart.
 */
public enum InputKind {
    EXPRESSION("expr"),
    MODULE("module");

    private final String encodedName;

    InputKind(String encodedName) {
        this.encodedName = encodedName;
    }

    /** Returns the stored and configured name of this kind. */
    public String encodedName() {
        return encodedName;
    }

    /** Returns the kind with this stored name, or empty when no kind has it. */
    public static Optional<InputKind> fromEncodedName(String encodedName) {
        Objects.requireNonNull(encodedName, "encodedName");
        for (var kind : values()) {
            if (kind.encodedName.equals(encodedName)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
