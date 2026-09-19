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
import io.github.tlaplus.hardening.common.ExprCounts;
import io.github.tlaplus.hardening.common.ExprEdge;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.MutatorConfig;
import io.github.tlaplus.hardening.config.QualityGateConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEntryValidator;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.corpus.InputAnalysis;
import io.github.tlaplus.hardening.corpus.ShallowPattern;
import io.github.tlaplus.hardening.corpus.StageResult;
import io.github.tlaplus.hardening.corpus.StoredEntry;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.workflow.execution.ElapsedTimeAccumulator;
import io.github.tlaplus.hardening.workflow.execution.StageCounters;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
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
        gate(corpus, config(0.3), counters).run(1);

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

        gate(corpus, config(1.0), counters()).run(0);

        assertEquals(Set.of(admissible), digests(corpus, CorpusVerdict.PASS));
        assertEquals(1, digests(corpus, CorpusVerdict.FAIL).size());
    }

    @Test
    void honorsTheEnabledShallowPatternsOnly(@TempDir Path directory) throws Exception {
        var corpus = corpus(directory);
        var counterOnly = aggregated(corpus, 1, 0, CorpusVerdict.PASS, explored(1, 1));
        var config = new QualityGateConfig(1.0, EnumSet.of(ShallowPattern.VACUOUS_PASS), 0, false);

        gate(corpus, config, counters()).run(0);

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

        gate(interrupted, config(0.5), counters()).run(0);
        gate(uninterrupted, config(0.5), counters()).run(0);

        assertEquals(digests(uninterrupted, CorpusVerdict.PASS), digests(interrupted, CorpusVerdict.PASS));
        assertEquals(3, digests(interrupted, CorpusVerdict.PASS).size());
        assertEquals(digests(uninterrupted, CorpusVerdict.FAIL), digests(interrupted, CorpusVerdict.FAIL));
    }

    @Test
    void roundsTheSelectionUpWithoutFloatingPointNoise(@TempDir Path directory) throws Exception {
        var gate = gate(corpus(directory), config(0.05), counters());
        assertEquals(5, gate.selected(100));
        assertEquals(1, gate.selected(1));
        assertEquals(0, gate.selected(0));
    }

    @Test
    void rejectsAnEntryWithoutAGeneration(@TempDir Path directory) throws Exception {
        var corpus = corpus(directory);
        aggregated(corpus, 1, null, CorpusVerdict.PASS, explored(1, 3));

        var failure = assertThrows(
                CorpusException.class, () -> gate(corpus, config(1.0), counters()).run(0));

        assertTrue(failure.getMessage().contains("records no generation"));
    }

    @Test
    void boundsEachBehaviourCellOverEveryGeneration(@TempDir Path directory) throws Exception {
        var corpus = corpus(directory);
        var earlier = aggregated(corpus, 1, 0, CorpusVerdict.PASS, explored(2, 4));
        gate(corpus, cells(1, false), counters()).run(0);
        // Inputs 2 and 3 share the cell of input 1, which generation 0 already fills; input 4 has
        // a cell of its own, and input 5 differs from input 1 in the TLC verdict only.
        aggregated(corpus, 2, 1, CorpusVerdict.PASS, explored(2, 4));
        aggregated(corpus, 3, 1, CorpusVerdict.PASS, explored(2, 5));
        var otherCell = aggregated(corpus, 4, 1, CorpusVerdict.PASS, explored(2, 8));
        var otherVerdict = aggregated(corpus, 5, 1, CorpusVerdict.COUNTEREXAMPLE, explored(2, 4));

        gate(corpus, cells(1, false), counters()).run(1);

        assertEquals(Set.of(earlier, otherCell, otherVerdict), digests(corpus, CorpusVerdict.PASS));
        assertEquals(2, digests(corpus, CorpusVerdict.FAIL).size());
    }

    @Test
    void keepsAnEntryOfAFullCellThatAddsCoverage(@TempDir Path directory) throws Exception {
        var corpus = corpus(directory);
        // One cell, ranked by input length: code 1 first, code 5 last.
        var first = aggregated(corpus, ranked(1, 1), 0, CorpusVerdict.PASS, explored(2, 4));
        aggregated(corpus, ranked(2, 2), 0, CorpusVerdict.PASS, explored(2, 4));
        var newEdge = aggregated(corpus, ranked(3, 3), 0, CorpusVerdict.PASS, explored(2, 4));
        aggregated(corpus, ranked(4, 4), 0, CorpusVerdict.PASS, explored(2, 4));
        var newBucket = aggregated(corpus, ranked(5, 5), 0, CorpusVerdict.PASS, explored(2, 4));

        gate(corpus, cells(1, true), counters()).run(0);

        // Code 1 fills the cell. Code 2 repeats it, code 3 adds an edge, code 4 repeats code 3's
        // edge, and code 5 repeats code 1's constructs often enough to change bucket.
        assertEquals(Set.of(first, newEdge, newBucket), digests(corpus, CorpusVerdict.PASS));
    }

    @Test
    void ignoresCoverageWhenCellsAreUnbounded(@TempDir Path directory) throws Exception {
        var corpus = corpus(directory);
        var best = aggregated(corpus, 1, 0, CorpusVerdict.PASS, explored(3, 4));
        aggregated(corpus, 3, 0, CorpusVerdict.PASS, explored(2, 4));

        gate(corpus, new QualityGateConfig(0.5, Set.of(), 0, true), input -> {
            throw new AssertionError("no replay without a cell bound");
        }, counters()).run(0);

        assertEquals(Set.of(best), digests(corpus, CorpusVerdict.PASS));
    }

    @Test
    void aRerunCompletesTheSamePlacementUnderCellsAndCoverage(@TempDir Path directory)
            throws Exception {
        var interrupted = corpus(directory.resolve("interrupted"));
        var uninterrupted = corpus(directory.resolve("uninterrupted"));
        // Codes 1 and 2 rank highest and share a cell; codes 3 to 5 share another. Within a cell,
        // the shorter input ranks first.
        var inputs = List.of(ranked(1, 1), ranked(2, 2), ranked(3, 1), ranked(4, 2), ranked(5, 3));
        for (var corpus : List.of(interrupted, uninterrupted)) {
            for (var input : inputs) {
                aggregated(corpus, input, 0, CorpusVerdict.PASS, explored(input[0] <= 2 ? 3 : 2, 4));
            }
        }
        // The interrupted gate committed its best pass, code 1, before stopping.
        var best = interrupted.resultEntries(CorpusStage.AGGREGATOR, CorpusVerdict.PASS).stream()
                .filter(entry -> entry.digest().equals(Digests.digest(inputs.get(0))))
                .findFirst()
                .orElseThrow();
        interrupted.completeQuality(best.path(), new StageResult(CorpusVerdict.PASS, Instant.EPOCH, Instant.EPOCH));

        gate(interrupted, cells(1, true), counters()).run(0);
        gate(uninterrupted, cells(1, true), counters()).run(0);

        assertEquals(digests(uninterrupted, CorpusVerdict.PASS), digests(interrupted, CorpusVerdict.PASS));
        assertEquals(digests(uninterrupted, CorpusVerdict.FAIL), digests(interrupted, CorpusVerdict.FAIL));
        // Code 1 fills the first cell, code 3 the second, code 5 adds a bucket; codes 2 and 4 add
        // nothing.
        assertEquals(
                Set.of(Digests.digest(inputs.get(0)), Digests.digest(inputs.get(2)), Digests.digest(inputs.get(4))),
                digests(interrupted, CorpusVerdict.PASS));
    }

    private static QualityGate gate(CorpusDirectory corpus, QualityGateConfig config, StageCounters counters) {
        return gate(corpus, config, QualityGateTest::analyze, counters);
    }

    private static QualityGate gate(
            CorpusDirectory corpus, QualityGateConfig config, InputAnalysis analysis, StageCounters counters) {
        return new QualityGate(corpus, config, analysis, counters);
    }

    /** Bounded cells, with or without coverage, keeping at most every admissible entry. */
    private static QualityGateConfig cells(int capacity, boolean coverage) {
        return new QualityGateConfig(1.0, MutatorConfig.defaults().gate().shallowPatterns(), capacity, coverage);
    }

    /** Returns an input of {@code code} that ranks {@code length}-th among inputs of one cell. */
    private static byte[] ranked(int code, int length) {
        var input = new byte[length];
        Arrays.fill(input, (byte) code);
        return input;
    }

    /**
     * A stand-in for replay, by the input's first byte, its code: codes 1 and 2 are one equality of
     * two integers, codes 3 and 4 add a set enumeration under the equality, and code 5 is code 1's
     * code four times over.
     */
    private static ExprCounts analyze(CorpusInput input) {
        var id = input.input()[0];
        var times = id == 5 ? 4L : 1L;
        var exprs = new TreeMap<String, Long>(Map.of("EQ", times, "TlaInt", 2 * times));
        var edges = new TreeMap<ExprEdge, Long>(Map.of(new ExprEdge("EQ", "TlaInt"), 2 * times));
        if (id == 3 || id == 4) {
            exprs.put("SET_ENUM", 1L);
            edges.put(new ExprEdge("EQ", "SET_ENUM"), 1L);
        }
        return new ExprCounts(3 * times, exprs, edges);
    }

    private static CorpusDirectory corpus(Path directory) throws Exception {
        return CorpusDirectory.initialize(directory, TomlConfig.render(FuzzTlaConfig.defaults()));
    }

    /** The gate of ADR 0010: the best share, without cells or coverage. */
    private static QualityGateConfig config(double selectFraction) {
        return new QualityGateConfig(selectFraction, MutatorConfig.defaults().gate().shallowPatterns(), 0, false);
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
        return aggregated(corpus, new byte[] {(byte) id}, generation, verdict, metrics);
    }

    private static String aggregated(
            CorpusDirectory corpus,
            byte[] input,
            Integer generation,
            CorpusVerdict verdict,
            ExplorationMetrics metrics)
            throws Exception {
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
