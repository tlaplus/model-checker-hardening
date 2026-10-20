package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import io.github.tlaplus.hardening.gen.InputKind;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The aggregator compares the state counts of TLC and TLC-n through the corpus (ADR 0019). */
class StateCountAggregationTest {
    private static final CheckingPolicy TLC_AND_TLCN =
            new CheckingPolicy(CheckerSet.of(CorpusStage.TLC, CorpusStage.TLCN), Oracle.CONFORMANCE);
    private static final byte[] INPUT = {7, 2};

    @TempDir Path directory;

    @Test
    void passesCompletedSearchesThatReachTheSameStates() throws Exception {
        assertEquals(CorpusVerdict.PASS, aggregate(7, 7));
    }

    /** Recovery re-judges the stored records and accepts the stored verdict. */
    @Test
    void failsCompletedSearchesThatReachDifferentStates() throws Exception {
        assertEquals(CorpusVerdict.FAIL, aggregate(7, 8));
    }

    /** Aggregates a TLC pass and a TLC-n pass and returns the verdict recovery counts. */
    private CorpusVerdict aggregate(long tlcStates, long tlcnStates) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), "");
        corpus.recoverAndValidate(CorpusEntryValidator.NONE, TLC_AND_TLCN);
        corpus.store(InputKind.EXPRESSION, INPUT);
        var parserPass = corpus.completeParser(corpus.inputPath(INPUT),
                new StageResult(CorpusVerdict.PASS, Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)));
        corpus.fanOutParserPass(parserPass);
        var name = parserPass.getFileName();
        var tlc = corpus.completeChecker(corpus.checkerInputPath(CorpusStage.TLC).resolve(name), pass(tlcStates));
        corpus.completeChecker(corpus.checkerInputPath(CorpusStage.TLCN).resolve(name), pass(tlcnStates));

        var input = corpus.aggregationInput(tlc).orElseThrow();
        corpus.completeAggregation(input, new StageResult(
                input.verdict(), Instant.ofEpochSecond(4), Instant.ofEpochSecond(5)));

        var counts = corpus.recoverAndValidate(CorpusEntryValidator.NONE, TLC_AND_TLCN)
                .counts(CorpusStage.AGGREGATOR);
        assertEquals(1, counts.count(input.verdict()));
        return input.verdict();
    }

    private static StageResult pass(long distinctStates) {
        var metrics = ExplorationMetrics.builder()
                .phase(ExplorationPhase.COMPLETE)
                .count(ExplorationCount.DISTINCT_STATES, distinctStates)
                .build();
        return new StageResult(CorpusVerdict.PASS, Instant.ofEpochSecond(3), Instant.ofEpochSecond(4),
                Optional.empty(), Optional.of(metrics), "");
    }
}
