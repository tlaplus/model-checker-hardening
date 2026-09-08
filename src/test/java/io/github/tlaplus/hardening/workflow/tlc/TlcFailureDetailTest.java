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

    @Test
    void unwrapsAnInvariantEvaluationFailure() {
        var diagnostic = """
                Error: Evaluating invariant Inv failed.
                Attempted to apply Head to the empty sequence.

                Error: The behavior up to this point is:
                """;

        assertEquals(
                "Attempted to apply Head to the empty sequence.",
                TlcFailureDetail.extract(diagnostic).orElseThrow());
    }

    @Test
    void unwrapsAnErrorThatEscapedAsAGenericException() {
        var diagnostic = """
                Error: TLC threw an unexpected exception.
                This was probably caused by an error in the spec or model.
                See the User Output or TLC Console for clues to what happened.
                The exception was a java.lang.RuntimeException
                : Attempted to evaluate a CASE with no conditions true.
                line 28, col 11 to line 160, col 62 of module FuzzInput
                """;

        assertEquals(
                "Attempted to evaluate a CASE with no conditions true.",
                TlcFailureDetail.extract(diagnostic).orElseThrow());
    }

    @Test
    void unwrapsAnErrorRaisedInsideAJavaModuleOverride() {
        var diagnostic = """
                Error: Attempted to apply the operator overridden by the Java method
                public static tlc2.value.impl.IntValue tlc2.module.Integers.Plus(\
                tlc2.value.impl.IntValue,tlc2.value.impl.IntValue),
                but it produced the following error:
                Cannot cast tlc2.value.impl.BoolValue to tlc2.value.impl.IntValue
                While working on the initial state:
                /\\ step = 0
                """;

        assertEquals(
                "Cannot cast tlc2.value.impl.BoolValue to tlc2.value.impl.IntValue",
                TlcFailureDetail.extract(diagnostic).orElseThrow());
    }

    @Test
    void unwrapsNestedWrappers() {
        var diagnostic = """
                Error: Evaluating invariant Inv failed.
                Attempted to apply the operator overridden by the Java method
                public static tlc2.value.impl.IntValue tlc2.module.Integers.Mod(\
                tlc2.value.impl.IntValue,tlc2.value.impl.IntValue),
                but it produced the following error:
                Cannot cast tlc2.value.impl.BoolValue to tlc2.value.impl.IntValue
                """;

        assertEquals(
                "Cannot cast tlc2.value.impl.BoolValue to tlc2.value.impl.IntValue",
                TlcFailureDetail.extract(diagnostic).orElseThrow());
    }

    @Test
    void keepsOnlyTheFirstLineOfTheReportedFailure() {
        var diagnostic = """
                Error: Evaluating invariant Inv failed.
                In applying the function
                <<>>,
                the first argument is:
                0
                which is not in its domain.
                """;

        assertEquals(
                "In applying the function",
                TlcFailureDetail.extract(diagnostic).orElseThrow());
    }

    @Test
    void keepsAWrapperThatIntroducesNothing() {
        var diagnostic = "Error: Evaluating invariant Inv failed.\n\nError: ignored";

        assertEquals(
                "Evaluating invariant Inv failed.",
                TlcFailureDetail.extract(diagnostic).orElseThrow());
    }
}
