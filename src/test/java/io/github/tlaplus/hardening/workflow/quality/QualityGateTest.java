package io.github.tlaplus.hardening.workflow.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import io.github.tlaplus.hardening.common.Digests;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.MutatorConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEntryValidator;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.corpus.ShallowPattern;
import io.github.tlaplus.hardening.corpus.StageResult;
import io.github.tlaplus.hardening.corpus.StoredEntry;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.workflow.execution.ElapsedTimeAccumulator;
import io.github.tlaplus.hardening.workflow.execution.StageCounters;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualityGateTest {
    @Test
    void keepsTheBestAdmissibleShareOfTheGenerationAndFailsTheRest(@TempDir Path directory)
            throws Exception {
        var corpus = corpus(directory);
        var best = aggregated(corpus, 1, 1, CorpusVerdict.PASS, explored(4, 5));
        var second = aggregated(corpus, 2, 1, CorpusVerdict.COUNTEREXAMPLE, explored(3, 5));
        var third = aggregated(corpus, 3, 1, CorpusVerdict.PASS, explored(2, 5));
        var vacuous = aggregated(corpus, 4, 1, CorpusVerdict.PASS, ExplorationMetrics.builder()
                .phase(ExplorationPhase.COMPLETE).count(ExplorationCount.INIT_STATES, 0).build());
        var failed = aggregated(corpus, 5, 1, CorpusVerdict.FAIL, explored(9, 9));
        var otherGeneration = aggregated(corpus, 6, 2, CorpusVerdict.PASS, explored(9, 9));
        var counters = counters();

        // ceil(0.3 × 5) = 2 of generation 1 are kept.
        new QualityGate(corpus, config(0.3), counters).run(1);

        assertEquals(Set.of(best, second), digests(corpus, CorpusVerdict.PASS));
        assertEquals(Set.of(third, vacuous, failed), digests(corpus, CorpusVerdict.FAIL));
        assertEquals(Set.of(otherGeneration), corpus.resultEntries(CorpusStage.AGGREGATOR, CorpusVerdict.PASS)
                .stream().map(StoredEntry::digest).collect(Collectors.toSet()));
        assertEquals(2, counters.count(CorpusVerdict.PASS));
        assertEquals(3, counters.count(CorpusVerdict.FAIL));
        var inventory = corpus.recoverAndValidate(CorpusEntryValidator.NONE);
        assertEquals(2, inventory.counts(CorpusStage.QUALITY).count(CorpusVerdict.PASS));
    }

    @Test
    void neverKeepsMoreThanTheAdmissibleEntries(@TempDir Path directory) throws Exception {
        var corpus = corpus(directory);
        var vacuous = ExplorationMetrics.builder().count(ExplorationCount.INIT_STATES, 0).build();
        aggregated(corpus, 1, 0, CorpusVerdict.PASS, vacuous);
        var admissible = aggregated(corpus, 2, 0, CorpusVerdict.PASS, explored(1, 3));

        new QualityGate(corpus, config(1.0), counters()).run(0);

        assertEquals(Set.of(admissible), digests(corpus, CorpusVerdict.PASS));
        assertEquals(1, digests(corpus, CorpusVerdict.FAIL).size());
    }

    @Test
    void honorsTheEnabledShallowPatternsOnly(@TempDir Path directory) throws Exception {
        var corpus = corpus(directory);
        var counterOnly = aggregated(corpus, 1, 0, CorpusVerdict.PASS, explored(1, 1));
        var config = new MutatorConfig(10, 1.0, 0.5, 1, MutatorConfig.defaults().weights(),
                EnumSet.of(ShallowPattern.VACUOUS_PASS));

        new QualityGate(corpus, config, counters()).run(0);

        assertEquals(Set.of(counterOnly), digests(corpus, CorpusVerdict.PASS));
    }

    @Test
    void aRerunAfterAnInterruptionCompletesTheSamePlacement(@TempDir Path directory)
            throws Exception {
        var interrupted = corpus(directory.resolve("interrupted"));
        var uninterrupted = corpus(directory.resolve("uninterrupted"));
        for (var corpus : List.of(interrupted, uninterrupted)) {
            for (var depth = 1; depth <= 6; depth++) {
                aggregated(corpus, depth, 0, CorpusVerdict.PASS, explored(depth, 4));
            }
        }
        // The interrupted gate committed its best pass, the deepest entry, before stopping.
        var best = interrupted.resultEntries(CorpusStage.AGGREGATOR, CorpusVerdict.PASS).stream()
                .filter(entry -> entry.digest().equals(Digests.digest(new byte[] {6})))
                .findFirst()
                .orElseThrow();
        interrupted.completeQuality(best.path(), new StageResult(CorpusVerdict.PASS, Instant.EPOCH, Instant.EPOCH));

        new QualityGate(interrupted, config(0.5), counters()).run(0);
        new QualityGate(uninterrupted, config(0.5), counters()).run(0);

        assertEquals(digests(uninterrupted, CorpusVerdict.PASS), digests(interrupted, CorpusVerdict.PASS));
        assertEquals(3, digests(interrupted, CorpusVerdict.PASS).size());
        assertEquals(digests(uninterrupted, CorpusVerdict.FAIL), digests(interrupted, CorpusVerdict.FAIL));
    }

    @Test
    void roundsTheSelectionUpWithoutFloatingPointNoise(@TempDir Path directory) throws Exception {
        var gate = new QualityGate(corpus(directory), config(0.05), counters());
        assertEquals(5, gate.selected(100));
        assertEquals(1, gate.selected(1));
        assertEquals(0, gate.selected(0));
    }

    @Test
    void rejectsAnEntryWithoutAGeneration(@TempDir Path directory) throws Exception {
        var corpus = corpus(directory);
        aggregated(corpus, 1, null, CorpusVerdict.PASS, explored(1, 3));

        var failure = assertThrows(
                CorpusException.class, () -> new QualityGate(corpus, config(1.0), counters()).run(0));

        assertTrue(failure.getMessage().contains("records no generation"));
    }

    private static CorpusDirectory corpus(Path directory) throws Exception {
        return CorpusDirectory.initialize(directory, TomlConfig.render(FuzzTlaConfig.defaults()));
    }

    private static MutatorConfig config(double selectFraction) {
        var defaults = MutatorConfig.defaults();
        return new MutatorConfig(10, selectFraction, 0.5, 1, defaults.weights(), defaults.shallowPatterns());
    }

    private static StageCounters counters() {
        return new StageCounters(StageVerdictSummary.empty(), new ElapsedTimeAccumulator());
    }

    private static ExplorationMetrics explored(long depth, long states) {
        return ExplorationMetrics.builder()
                .phase(ExplorationPhase.COMPLETE)
                .count(ExplorationCount.INIT_STATES, 1)
                .count(ExplorationCount.PROJECTED_DEPTH, depth)
                .count(ExplorationCount.PROJECTED_STATES, states)
                .count(ExplorationCount.ACTIONS_DISCOVERING, 1)
                .build();
    }

    /**
     * Stores the one-byte input {@code id} of {@code generation} (none when {@code null}), passes
     * it through both checkers with the same verdict and TLC metrics, aggregates it, and returns its
     * digest.
     */
    private static String aggregated(
            CorpusDirectory corpus,
            int id,
            Integer generation,
            CorpusVerdict verdict,
            ExplorationMetrics metrics)
            throws Exception {
        var input = new byte[] {(byte) id};
        if (generation == null) {
            corpus.store(InputKind.EXPRESSION, input);
        } else {
            corpus.store(InputKind.EXPRESSION, input, GenerationMetadata.generated(generation, 0, 0.0));
        }
        var parserPass = corpus.completeParser(
                corpus.inputPath(input), new StageResult(CorpusVerdict.PASS, Instant.EPOCH, Instant.EPOCH));
        corpus.fanOutParserPass(parserPass);
        var name = parserPass.getFileName();
        var tlc = corpus.completeChecker(
                corpus.checkerInputPath(CorpusStage.TLC).resolve(name), checkerResult(verdict, Optional.of(metrics)));
        corpus.completeChecker(
                corpus.checkerInputPath(CorpusStage.APALACHE).resolve(name), checkerResult(verdict, Optional.empty()));
        corpus.completeAggregation(
                corpus.aggregationInput(tlc).orElseThrow(),
                new StageResult(CorpusVerdict.PASS, Instant.EPOCH, Instant.EPOCH));
        return Digests.digest(input);
    }

    private static StageResult checkerResult(CorpusVerdict verdict, Optional<ExplorationMetrics> metrics) {
        var failure = verdict == CorpusVerdict.FAIL
                ? Optional.of(new CheckerFailure(CheckerFailureCode.SPEC_EVAL, Optional.empty()))
                : Optional.<CheckerFailure>empty();
        return new StageResult(verdict, Instant.EPOCH, Instant.EPOCH, failure, metrics, "");
    }

    private static Set<String> digests(CorpusDirectory corpus, CorpusVerdict verdict) throws Exception {
        return corpus.resultEntries(CorpusStage.QUALITY, verdict).stream()
                .map(StoredEntry::digest)
                .collect(Collectors.toSet());
    }
}
