package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.gen.InputKind;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The fuzzing technique a corpus runs (`fuzztla run --how`). A corpus records its technique on its
 * first run, so every later run and every replay decodes and judges its entries the same way.
 *
 * <p>The encoded name is part of the corpus format and of the command line.
 */
public enum Technique {
    /** Property-based generation and mutation, judged by conformance of TLC and Apalache. */
    PBT("pbt", Oracle.CONFORMANCE, EnumSet.allOf(InputKind.class), Ranking.DEPTH),
    /** Metamorphic testing: a base and its rewrite, judged by the metamorphic relation (ADR 0016). */
    MT("mt", Oracle.METAMORPHIC, EnumSet.of(InputKind.EXPRESSION, InputKind.MODULE), Ranking.DEPTH),
    /**
     * Graph-based testing: control graphs whose target the graph predicts (ADR 0023), whose parents
     * are selected for node actions that produce new data (ADR 0024).
     */
    GBT("gbt", Oracle.GRAPH, EnumSet.of(InputKind.GRAPH), Ranking.ACTION_COVERAGE);

    /** The technique of a corpus that records none, which every corpus did before ADR 0016. */
    public static final Technique UNRECORDED = PBT;

    private final String encodedName;
    private final Oracle oracle;
    private final Set<InputKind> kinds;
    private final Ranking ranking;

    Technique(String encodedName, Oracle oracle, Set<InputKind> kinds, Ranking ranking) {
        this.encodedName = encodedName;
        this.oracle = oracle;
        this.kinds = Set.copyOf(kinds);
        this.ranking = ranking;
    }

    /** Returns the name used by `--how` and in the corpus. */
    public String encodedName() {
        return encodedName;
    }

    /** Returns how the aggregator judges the entries of a corpus that runs this technique. */
    public Oracle oracle() {
        return oracle;
    }

    /** Returns how the quality gate ranks the entries of a corpus that runs this technique. */
    public Ranking ranking() {
        return ranking;
    }

    /** Reports whether a run of this technique may generate inputs of {@code kind}. */
    public boolean generates(InputKind kind) {
        return kinds.contains(Objects.requireNonNull(kind, "kind"));
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
