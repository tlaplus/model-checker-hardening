package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ParserWorkerMainTest {
    @Test
    void formatsParserExceptionsWithTheirStackTrace() {
        var diagnostic =
                ParserWorkerMain.stackTrace(new IllegalStateException("parser exploded"));

        assertTrue(diagnostic.contains("java.lang.IllegalStateException: parser exploded"));
        assertTrue(diagnostic.contains("ParserWorkerMainTest"));
    }
}
