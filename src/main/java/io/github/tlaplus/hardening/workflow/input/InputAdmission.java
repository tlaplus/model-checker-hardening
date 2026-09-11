package io.github.tlaplus.hardening.workflow.input;

import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.signature.KnownDefectDatabase;
import io.github.tlaplus.hardening.signature.KnownDefectMatch;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.spec.SpecText;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Decides whether a decoded candidate enters {@code 00-inputs}, and where a known defect goes.
 *
 * <p>The checks run in a fixed order: the richness threshold of the target's cohort (ADR 0002),
 * the worker request-frame limit, and last the known-defect signatures. A signature count
 * therefore means "would otherwise have been admitted". None of the checks draws randomness, so
 * the candidate stream is the same with or without a database.
 */
public final class InputAdmission {
    private final PbtConfig pbt;
    private final KnownDefectDatabase knownDefects;
    private final KnownDefectQuarantine quarantine;

    public InputAdmission(
            PbtConfig pbt, KnownDefectDatabase knownDefects, KnownDefectQuarantine quarantine) {
        this.pbt = Objects.requireNonNull(pbt, "pbt");
        this.knownDefects = Objects.requireNonNull(knownDefects, "knownDefects");
        this.quarantine = Objects.requireNonNull(quarantine, "quarantine");
    }

    /** Admits by richness and size alone, as a run without a known-defect database does. */
    public static InputAdmission withoutKnownDefects(PbtConfig pbt) {
        return new InputAdmission(pbt, KnownDefectDatabase.empty(), KnownDefectQuarantine.discarding());
    }

    /** The outcome of admission for one candidate. */
    sealed interface Decision {
        record Admitted() implements Decision {}

        record BelowRichnessThreshold() implements Decision {}

        /**
         * A module that renders past the worker request frame cannot reach the parser or the
         * checkers, so it is rejected rather than stored as an entry they can only crash on.
         */
        record ExceedsRequestFrame() implements Decision {}

        /** The ids of every matching signature, in database order; the first is the primary. */
        record KnownDefect(List<String> signatures) implements Decision {
            public KnownDefect {
                signatures = List.copyOf(signatures);
                if (signatures.isEmpty()) {
                    throw new IllegalArgumentException("a known defect names at least one signature");
                }
            }

            String primary() {
                return signatures.getFirst();
            }
        }
    }

    PbtConfig pbt() {
        return pbt;
    }

    Decision decide(SpecArtifact artifact, double richness, double threshold) {
        Objects.requireNonNull(artifact, "artifact");
        if (richness < threshold) {
            return new Decision.BelowRichnessThreshold();
        }
        if (!SpecText.withinWorkerProtocolLimit(artifact.module())) {
            return new Decision.ExceedsRequestFrame();
        }
        var matches = knownDefects.matches(artifact.module(), FuzzInputModule.ENTRY_POINTS);
        if (!matches.isEmpty()) {
            return new Decision.KnownDefect(
                    matches.stream().map(KnownDefectMatch::defect).map(defect -> defect.id()).toList());
        }
        return new Decision.Admitted();
    }

    /** Keeps a known-defect candidate in the quarantine while its signature has room. */
    KnownDefectQuarantine.Outcome quarantine(
            InputKind kind, byte[] input, GenerationMetadata generation)
            throws IOException, CorpusException {
        return quarantine.record(kind, input, generation);
    }
}
