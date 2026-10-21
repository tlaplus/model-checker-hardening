package io.github.tlaplus.hardening.architecture.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JavadocTextTest {
    @Test
    void shortensLinkSignatures() {
        assertEquals("CorpusStage", JavadocText.simpleName("io.github.tlaplus.hardening.corpus.CorpusStage"));
        assertEquals("Draw.next", JavadocText.simpleName("gen.Draw#next(int)"));
        assertEquals("size", JavadocText.simpleName("#size()"));
    }
}
