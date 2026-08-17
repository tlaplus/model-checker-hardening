package io.github.tlaplus.hardening.workflow.tlc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TlcFailureDetailTest {
    @Test
    void extractsTheFirstMeaningfulErrorLine() {
        var diagnostic = "TLC summary\nError:\n Error:  Attempted   to apply Head.  \n"
                + "Error: ignored";

        assertEquals(
                "Attempted to apply Head.",
                TlcFailureDetail.extract(diagnostic).orElseThrow());
    }

    @Test
    void omitsDiagnosticsWithoutAnErrorLine() {
        assertTrue(TlcFailureDetail.extract("TLC completed without details").isEmpty());
    }

    @Test
    void truncatesByUnicodeCodePoints() {
        var detail = "x".repeat(78) + "😀yz";

        var truncated = TlcFailureDetail.extract("Error: " + detail).orElseThrow();

        assertEquals("x".repeat(78) + "😀…", truncated);
        assertEquals(80, truncated.codePointCount(0, truncated.length()));
    }
}
