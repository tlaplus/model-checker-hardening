package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.common.GeneratorAggregate;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/** Stable field identity, independent of screen coordinates or formatted text. */
sealed interface RunMetric {
    /** Run-wide fields; each carries its report label, live-screen label, and report section. */
    enum Field implements RunMetric {
        GENERATION(Section.SUMMARY, "Generation", "Gen", view -> new RunValue.Count(view.generation())),
        ENTRIES(Section.SUMMARY, "Corpus entries", "Entries", view -> new RunValue.Count(view.corpusEntries())),
        TOTAL_ELAPSED(Section.SUMMARY, "Total elapsed", "Elapsed", view -> new RunValue.Elapsed(view.elapsed())),
        GENERATED(Section.INPUTS, "Generated inputs", "Admitted",
                view -> new RunValue.Count(view.generator().generated())),
        ATTEMPTS(Section.INPUTS, "Candidate attempts", "Attempts", view -> count(view, GeneratorAggregate::attempts)),
        REJECTED(Section.INPUTS, "Generator rejected", "Rejected", view -> count(view, GeneratorAggregate::rejected)),
        LOW_RICHNESS(Section.INPUTS, "Richness rejected", "Low richness",
                view -> count(view, GeneratorAggregate::richnessRejected)),
        DUPLICATES(Section.INPUTS, "Duplicate inputs", "Duplicates", view -> count(view, GeneratorAggregate::duplicates)),
        CLONES(Section.INPUTS, "Mutant clones", "Clones", view -> count(view, GeneratorAggregate::clones)),
        KNOWN_DEFECTS(Section.INPUTS, "Known defects", "Known defects",
                view -> count(view, GeneratorAggregate::knownDefectRejections)),
        MIN_RICHNESS(Section.INPUTS, "Min richness", "min", view -> richness(view, GeneratorAggregate.Richness::minimum)),
        AVG_RICHNESS(Section.INPUTS, "Avg richness", "avg", view -> richness(view, GeneratorAggregate.Richness::average)),
        MAX_RICHNESS(Section.INPUTS, "Max richness", "max", view -> richness(view, GeneratorAggregate.Richness::maximum)),
        GENERATOR_ELAPSED(Section.INPUTS, "Generator elapsed", StageElapsed.SHORT_LABEL,
                view -> new RunValue.Elapsed(view.generator().elapsed()));

        /** The final-report group a field belongs to; declaration order is report order. */
        enum Section {
            SUMMARY,
            INPUTS
        }

        private final Section section;
        private final String label;
        private final String shortLabel;
        private final Function<RunView, RunValue> read;

        Field(Section section, String label, String shortLabel, Function<RunView, RunValue> read) {
            this.section = section;
            this.label = label;
            this.shortLabel = shortLabel;
            this.read = read;
        }

        Section section() {
            return section;
        }

        /** The descriptive label of the final report. */
        String label() {
            return label;
        }

        /** The terse label of the live screen. */
        String shortLabel() {
            return shortLabel;
        }

        RunValue read(RunView view) {
            return read.apply(view);
        }

        private static RunValue count(RunView view, ToLongFunction<GeneratorAggregate> counter) {
            return new RunValue.Count(counter.applyAsLong(view.generator().aggregate()));
        }

        private static RunValue richness(RunView view, ToDoubleFunction<GeneratorAggregate.Richness> statistic) {
            var richness = view.generator().aggregate().richness();
            return RunValue.Richness.of(richness.samples(), statistic.applyAsDouble(richness));
        }
    }

    enum State implements RunMetric { STATUS }

    record Queue(CorpusStage stage) implements RunMetric {
        static final String LABEL = "Queued";
    }

    record Result(CorpusStage stage, CorpusVerdict verdict) implements RunMetric {}

    record StageElapsed(CorpusStage stage) implements RunMetric {
        static final String LABEL = "Summed worker time";
        static final String SHORT_LABEL = "Work";
    }
}
