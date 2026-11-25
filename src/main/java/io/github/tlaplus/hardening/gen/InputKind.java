package io.github.tlaplus.hardening.gen;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
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
@ArchRole(Role.MODEL)
@Key
public enum InputKind {
    EXPRESSION("expr", false),
    MODULE("module", false),
    /** A control graph tracked by {@code pc}, whose target the graph oracle predicts (ADR 0023). */
    GRAPH("graph", false),
    /**
     * A recorded counterexample of a generated input, which every checker replays (ADR 0034). The
     * workflow derives it from a checker's output; no generator draws it from bytes.
     */
    TRACE("trace", true);

    private final String encodedName;
    private final boolean derived;

    InputKind(String encodedName, boolean derived) {
        this.encodedName = encodedName;
        this.derived = derived;
    }

    /** Returns the stored and configured name of this kind. */
    public String encodedName() {
        return encodedName;
    }

    /**
     * Reports whether the workflow derives inputs of this kind from checker output rather than
     * generating them (ADR 0034). Such an input predicts its own outcome, which every technique
     * judges, and no run generates or mutates it.
     */
    public boolean derived() {
        return derived;
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
