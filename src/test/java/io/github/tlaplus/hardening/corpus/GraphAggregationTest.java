package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.InputKind;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The aggregator judges a graph entry against the outcome its input predicts, and recovery
 * re-judges the stored entry against the same prediction (ADR 0023 §5).
 */
class GraphAggregationTest {
    private static final byte[] INPUT = {7, 2};

    @TempDir Path directory;

    @Test
    void passesTheVerdictTheInputPredicts() throws Exception {
        var policy = policy(ExpectedOutcome.counterexample(2));
        assertEquals(CorpusVerdict.PASS, aggregate(policy, CorpusVerdict.COUNTEREXAMPLE));
    }

    /** A checker that agrees with itself still fails the entry when it contradicts the graph. */
    @Test
    void failsAPassWhereTheGraphReachesItsTarget() throws Exception {
        var policy = policy(ExpectedOutcome.counterexample(2));
        assertEquals(CorpusVerdict.FAIL, aggregate(policy, CorpusVerdict.PASS));
    }

    @Test
    void recoveryRejectsAStoredVerdictThatAnotherPredictionContradicts() throws Exception {
        var corpus = corpus(policy(ExpectedOutcome.pass()));
        aggregate(corpus, CorpusVerdict.PASS);
        var failure = assertThrows(CorpusException.class,
                () -> corpus.recoverAndValidate(CorpusEntryValidator.NONE, policy(ExpectedOutcome.counterexample(1))));
        assertTrue(failure.getMessage().contains("aggregator verdict does not match"), failure.getMessage());
    }

    /** Aggregates one TLC result and returns the verdict that recovery counts. */
    private CorpusVerdict aggregate(CheckingPolicy policy, CorpusVerdict tlc) throws Exception {
        var corpus = corpus(policy);
        var verdict = aggregate(corpus, tlc);
        var counts = corpus.recoverAndValidate(CorpusEntryValidator.NONE, policy).counts(CorpusStage.AGGREGATOR);
        assertEquals(1, counts.count(verdict));
        return verdict;
    }

    private CorpusDirectory corpus(CheckingPolicy policy) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), "");
        corpus.recoverAndValidate(CorpusEntryValidator.NONE, policy);
        return corpus;
    }

    private static CorpusVerdict aggregate(CorpusDirectory corpus, CorpusVerdict tlc) throws Exception {
        corpus.store(InputKind.GRAPH, INPUT);
        var parserPass = corpus.completeParser(corpus.inputPath(INPUT),
                new StageResult(CorpusVerdict.PASS, Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)));
        corpus.fanOutParserPass(parserPass);
        var result = corpus.completeChecker(corpus.checkerInputPath(CorpusStage.TLC).resolve(parserPass.getFileName()),
                new StageResult(tlc, Instant.ofEpochSecond(3), Instant.ofEpochSecond(4)));
        var input = corpus.aggregationInput(result).orElseThrow();
        corpus.completeAggregation(input, new StageResult(
                input.verdict(), Instant.ofEpochSecond(4), Instant.ofEpochSecond(5)));
        return input.verdict();
    }

    /** TLC alone, judged by the graph oracle against {@code expected} for every graph input. */
    private static CheckingPolicy policy(ExpectedOutcome expected) {
        return new CheckingPolicy(CheckerSet.of(CorpusStage.TLC), Oracle.GRAPH,
                input -> input.kind() == InputKind.GRAPH ? Optional.of(expected) : Optional.empty());
    }
}
