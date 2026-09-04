package io.github.tlaplus.hardening.common;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DiagnosticsTest {
    @Test
    void formatsExceptionsWithTheirStackTrace() {
        var diagnostic =
                Diagnostics.stackTrace(new IllegalStateException("operation exploded"));

        assertTrue(diagnostic.contains("java.lang.IllegalStateException: operation exploded"));
        assertTrue(diagnostic.contains("DiagnosticsTest"));
    }
}
