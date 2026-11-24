package io.github.tlaplus.hardening.workflow.generic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.config.GenericCompatibility;
import io.github.tlaplus.hardening.workflow.worker.CheckKind;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GenericConventionTest {
    private static final GenericConvention TLC = GenericConvention.of(GenericCompatibility.TLC);

    /** Manyan's documented exit statuses, which follow TLC's (ADR 0033 §2, probe P2). */
    @Test
    void classifiesManyansExitStatusesAsTlcDoes() {
        assertEquals(StageOutcome.PASS, outcome(0));
        for (var violation : new int[] {10, 11, 12, 13, 14}) {
            assertEquals(StageOutcome.COUNTEREXAMPLE, outcome(violation), Integer.toString(violation));
        }
        for (var evaluation : new int[] {75, 76, 77}) {
            assertEquals(Optional.of(CheckerFailureCode.SPEC_EVAL), failure(evaluation, ""), Integer.toString(evaluation));
        }
        assertEquals(Optional.of(CheckerFailureCode.PARSE), failure(150, ""));
        assertEquals(Optional.of(CheckerFailureCode.PARSE), failure(151, ""));
        assertEquals(StageOutcome.CRASH, outcome(255));
    }

    /** TLC's command line reports integer overflow with status 255; it is an evaluation failure. */
    @Test
    void classifiesIntegerOverflowAsAnEvaluationFailure() {
        assertEquals(Optional.of(CheckerFailureCode.SPEC_EVAL),
                failure(255, "Error: TLC can't handle a number this big.\n"));
    }

    @Test
    void writesTlcsConfigurationForTlcCompatibility() {
        assertEquals("SPECIFICATION Spec\nINVARIANT Inv\nPROPERTY Prop\n",
                TLC.configuration().apply(new CheckRequest(3, CheckKind.INVARIANT_AND_PROPERTY)));
    }

    /** The entry points Apalache's command line names, as a TLC-syntax file without fairness. */
    @Test
    void writesApalachesEntryPointsForApalacheCompatibility() {
        var apalache = GenericConvention.of(GenericCompatibility.APALACHE).configuration();
        assertEquals("INIT Init\nNEXT Next\nINVARIANT Inv\n", apalache.apply(CheckRequest.invariant(2)));
        assertEquals("INIT Init\nNEXT Next\nINVARIANT Inv\nPROPERTY Liveness\n",
                apalache.apply(new CheckRequest(2, CheckKind.INVARIANT_AND_PROPERTY)));
        assertEquals("INIT Init\nNEXT Next\nINVARIANT Inv\nINVARIANT Step\n",
                apalache.apply(new CheckRequest(2, CheckKind.RELATION)));
        assertEquals("INIT InitC\nNEXT NextC\nPROPERTY PropC\n",
                apalache.apply(new CheckRequest(2, CheckKind.CHECKED_PROPERTY)));
    }

    private static StageOutcome outcome(int status) {
        return TLC.classifier().classify(status, "").outcome();
    }

    private static Optional<CheckerFailureCode> failure(int status, String diagnostic) {
        ToolResult result = TLC.classifier().classify(status, diagnostic);
        return result.failureCode();
    }
}
