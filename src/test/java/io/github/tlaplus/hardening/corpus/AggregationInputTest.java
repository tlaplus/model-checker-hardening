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
                input(CorpusVerdict.PASS, CorpusVerdict.PASS).conformanceVerdict());
        assertEquals(
                CorpusVerdict.PASS,
                input(CorpusVerdict.FAIL, CorpusVerdict.FAIL).conformanceVerdict());
    }

    @Test
    void failsWhenTheCheckersDisagree() {
        assertEquals(
                CorpusVerdict.FAIL,
                input(CorpusVerdict.PASS, CorpusVerdict.FAIL).conformanceVerdict());
        assertEquals(
                CorpusVerdict.FAIL,
                input(CorpusVerdict.FAIL, CorpusVerdict.PASS).conformanceVerdict());
    }

    @Test
    void requiresEveryNonCrashCheckerVerdict() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AggregationInput(
                        Path.of("candidate.cbor"),
                        Map.of(CorpusStage.TLC, CorpusVerdict.PASS)));
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
                        apalache));
    }
}
