package io.github.tlaplus.hardening.database;

import io.github.tlaplus.hardening.common.Diagnostics;

import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.InputAnalysis;
import io.github.tlaplus.hardening.corpus.ReplayedInput;
import java.util.Objects;

/** The result of analysing one input: what its replay shows, or why it could not be replayed. */
sealed interface ReplayOutcome {
    record Replayed(ReplayedInput replayed) implements ReplayOutcome {
        public Replayed {
            Objects.requireNonNull(replayed, "replayed");
        }
    }

    record Failed(String error) implements ReplayOutcome {
        public Failed {
            Objects.requireNonNull(error, "error");
        }
    }

    /** Runs {@code analysis} on {@code input}, turning a failure of this input into an outcome. */
    static ReplayOutcome of(InputAnalysis analysis, CorpusInput input) {
        try {
            return new Replayed(analysis.analyze(input));
        } catch (RuntimeException | StackOverflowError failure) {
            return new Failed(Diagnostics.message(failure));
        }
    }
}
