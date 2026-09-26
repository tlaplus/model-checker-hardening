package io.github.tlaplus.hardening.corpus;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The fuzzing technique a corpus runs (`fuzztla run --how`). A corpus records its technique on its
 * first run, so every later run and every replay decodes and judges its entries the same way.
 *
 * <p>The encoded name is part of the corpus format and of the command line.
 */
public enum Technique {
    PBT("pbt", Oracle.CONFORMANCE);

    /** The technique of a corpus that records none, which every corpus did before ADR 0016. */
    public static final Technique UNRECORDED = PBT;

    private final String encodedName;
    private final Oracle oracle;

    Technique(String encodedName, Oracle oracle) {
        this.encodedName = encodedName;
        this.oracle = oracle;
    }

    /** Returns the name used by `--how` and in the corpus. */
    public String encodedName() {
        return encodedName;
    }

    /** Returns how the aggregator judges the entries of a corpus that runs this technique. */
    public Oracle oracle() {
        return oracle;
    }

    /** Returns the technique with this encoded name, or empty when none has it. */
    public static Optional<Technique> fromEncodedName(String encodedName) {
        Objects.requireNonNull(encodedName, "encodedName");
        return Arrays.stream(values())
                .filter(technique -> technique.encodedName.equals(encodedName))
                .findFirst();
    }

    /** Returns every encoded name, in declaration order, for diagnostics. */
    public static List<String> encodedNames() {
        return Arrays.stream(values()).map(Technique::encodedName).toList();
    }
}
