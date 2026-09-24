package io.github.tlaplus.hardening.corpus;

import static io.github.tlaplus.hardening.corpus.CorpusVerdict.COUNTEREXAMPLE;
import static io.github.tlaplus.hardening.corpus.CorpusVerdict.FAIL;
import static io.github.tlaplus.hardening.corpus.CorpusVerdict.PASS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class OracleTest {
    @Test
    void conformancePassesWhenTheCheckersAgree() {
        assertEquals(PASS, Oracle.CONFORMANCE.judge(List.of(COUNTEREXAMPLE, COUNTEREXAMPLE)));
        assertEquals(FAIL, Oracle.CONFORMANCE.judge(List.of(PASS, FAIL)));
    }

    /** A counterexample violates the relation even when every checker reports it (ADR 0016 §4). */
    @Test
    void metamorphicPassesWhenTheCheckersAgreeOnAnythingButACounterexample() {
        assertEquals(PASS, Oracle.METAMORPHIC.judge(List.of(PASS, PASS)));
        assertEquals(PASS, Oracle.METAMORPHIC.judge(List.of(FAIL, FAIL)));
        assertEquals(PASS, Oracle.METAMORPHIC.judge(List.of(PASS)));
        assertEquals(FAIL, Oracle.METAMORPHIC.judge(List.of(COUNTEREXAMPLE, COUNTEREXAMPLE)));
        assertEquals(FAIL, Oracle.METAMORPHIC.judge(List.of(COUNTEREXAMPLE)));
        assertEquals(FAIL, Oracle.METAMORPHIC.judge(List.of(PASS, FAIL)));
    }
}
