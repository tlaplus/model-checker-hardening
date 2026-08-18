package io.github.tlaplus.hardening.checker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class CheckerFailureTest {
    @Test
    void decodesTheSharedNumericRegistry() {
        assertEquals(
                CheckerFailureCode.COUNTEREXAMPLE,
                CheckerFailureCode.fromEncodedCode(12));
        assertEquals(CheckerFailureCode.SPEC_EVAL, CheckerFailureCode.fromEncodedCode(75));
        assertEquals(CheckerFailureCode.TYPECHECK, CheckerFailureCode.fromEncodedCode(120));
        assertEquals(CheckerFailureCode.PARSE, CheckerFailureCode.fromEncodedCode(150));
        assertThrows(
                IllegalArgumentException.class,
                () -> CheckerFailureCode.fromEncodedCode(76));
    }

    @Test
    void boundsOptionalDetailsByUnicodeCodePoints() {
        var detail = "x".repeat(79) + "😀";

        assertEquals(
                detail,
                new CheckerFailure(CheckerFailureCode.SPEC_EVAL, Optional.of(detail))
                        .detail()
                        .orElseThrow());
        assertThrows(
                IllegalArgumentException.class,
                () -> new CheckerFailure(
                        CheckerFailureCode.SPEC_EVAL, Optional.of(detail + "x")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CheckerFailure(
                        CheckerFailureCode.SPEC_EVAL, Optional.of("  ")));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CheckerFailure(
                        CheckerFailureCode.SPEC_EVAL, Optional.of("first\nsecond")));
    }

    @Test
    void normalizesFailureDetails() {
        assertTrue(CheckerFailure.normalizeDetail(" \t\n ").isEmpty());
        assertEquals(
                Optional.of("first second"),
                CheckerFailure.normalizeDetail("  first \n\t second  "));

        var normalized = CheckerFailure.normalizeDetail("x".repeat(78) + "😀yz")
                .orElseThrow();
        assertEquals("x".repeat(78) + "😀…", normalized);
        assertEquals(80, normalized.codePointCount(0, normalized.length()));
        assertEquals(
                normalized,
                new CheckerFailure(CheckerFailureCode.SPEC_EVAL, Optional.of(normalized))
                        .detail()
                        .orElseThrow());
    }
}
