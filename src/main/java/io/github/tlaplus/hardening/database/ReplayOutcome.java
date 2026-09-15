package io.github.tlaplus.hardening.database;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.common.ExprCounts;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import java.util.Objects;

/** The result of analysing one input: its counts, or why the input could not be replayed. */
sealed interface ReplayOutcome {
    record Replayed(ExprCounts counts) implements ReplayOutcome {
        public Replayed {
            Objects.requireNonNull(counts, "counts");
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
