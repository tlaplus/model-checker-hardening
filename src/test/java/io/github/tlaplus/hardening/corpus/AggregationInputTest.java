package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tlaplus.hardening.gen.InputKind;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AggregationInputTest {
    @Test
    void passesWhenTheCheckersAgree() {
        assertEquals(
                CorpusVerdict.PASS,
                input(CorpusVerdict.PASS, CorpusVerdict.PASS).verdict());
        assertEquals(
                CorpusVerdict.PASS,
                input(CorpusVerdict.COUNTEREXAMPLE, CorpusVerdict.COUNTEREXAMPLE)
                        .verdict());
        assertEquals(
                CorpusVerdict.PASS,
                input(CorpusVerdict.FAIL, CorpusVerdict.FAIL).verdict());
    }

    @Test
    void failsWhenTheCheckersDisagree() {
        assertEquals(
                CorpusVerdict.FAIL,
                input(CorpusVerdict.PASS, CorpusVerdict.FAIL).verdict());
        assertEquals(
                CorpusVerdict.FAIL,
                input(CorpusVerdict.FAIL, CorpusVerdict.PASS).verdict());
        assertEquals(
                CorpusVerdict.FAIL,
                input(CorpusVerdict.COUNTEREXAMPLE, CorpusVerdict.PASS)
                        .verdict());
        assertEquals(
                CorpusVerdict.FAIL,
                input(CorpusVerdict.PASS, CorpusVerdict.COUNTEREXAMPLE)
                        .verdict());
        assertEquals(
                CorpusVerdict.FAIL,
                input(CorpusVerdict.COUNTEREXAMPLE, CorpusVerdict.FAIL)
                        .verdict());
        assertEquals(
                CorpusVerdict.FAIL,
                input(CorpusVerdict.FAIL, CorpusVerdict.COUNTEREXAMPLE)
                        .verdict());
    }

    @Test
    void requiresEveryNonCrashCheckerVerdict() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AggregationInput(
                        Path.of("candidate.cbor"),
                        InputKind.MODULE,
                        Map.of(CorpusStage.TLC, record(CorpusVerdict.PASS)),
                        CheckingPolicy.DEFAULT));
        assertThrows(
                IllegalArgumentException.class,
                () -> input(CorpusVerdict.CRASH, CorpusVerdict.PASS));
    }

    /**
     * A conformance corpus judges a replayed trace by its prediction (ADR 0034 §4): two checkers that
     * agree on a pass refute the counterexample the trace predicts.
     */
    @Test
    void judgesATraceEntryByItsPredictionUnderConformance() {
        var predicted = Optional.of(ExpectedOutcome.counterexample(0));
        var results = Map.of(CorpusStage.TLC, record(CorpusVerdict.PASS), CorpusStage.APALACHE, record(CorpusVerdict.PASS));
        var policy = new CheckingPolicy(CheckerSet.DEFAULT, Oracle.CONFORMANCE, input -> predicted);

        assertEquals(CorpusVerdict.FAIL,
                new AggregationInput(Path.of("candidate.cbor"), InputKind.TRACE, results, policy, predicted).verdict());
        assertEquals(CorpusVerdict.PASS,
                new AggregationInput(Path.of("candidate.cbor"), InputKind.MODULE, results, policy, Optional.empty())
                        .verdict());
    }

    private static AggregationInput input(CorpusVerdict tlc, CorpusVerdict apalache) {
        return new AggregationInput(
                Path.of("candidate.cbor"),
                InputKind.MODULE,
                Map.of(
                        CorpusStage.TLC,
                        record(tlc),
                        CorpusStage.APALACHE,
                        record(apalache)),
                CheckingPolicy.DEFAULT);
    }

    private static StageRecord record(CorpusVerdict verdict) {
        return new StageRecord(verdict, Instant.EPOCH, Instant.EPOCH);
    }
}
