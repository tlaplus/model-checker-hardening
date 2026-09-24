package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Map;
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
                        Map.of(CorpusStage.TLC, CorpusVerdict.PASS),
                        Oracle.CONFORMANCE));
        assertThrows(
                IllegalArgumentException.class,
                () -> input(CorpusVerdict.CRASH, CorpusVerdict.PASS));
    }

    private static AggregationInput input(CorpusVerdict tlc, CorpusVerdict apalache) {
        return new AggregationInput(
                Path.of("candidate.cbor"),
                Map.of(
                        CorpusStage.TLC,
                        tlc,
                        CorpusStage.APALACHE,
                        apalache),
                Oracle.CONFORMANCE);
    }
}
