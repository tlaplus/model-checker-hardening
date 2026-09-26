package io.github.tlaplus.hardening.workflow.tlc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import org.junit.jupiter.api.Test;

class TlcConfigurationTest {
    @Test
    void namesTheSpecificationAndInvariantWithoutAStateConstraint() {
        assertEquals("SPECIFICATION Spec\nINVARIANT Inv\n", TlcConfiguration.text(CheckRequest.invariant(5)));
    }

    @Test
    void namesThePropertyOnlyWhenTheRequestAsksForIt() {
        assertEquals("SPECIFICATION Spec\nINVARIANT Inv\nPROPERTY Prop\n",
                TlcConfiguration.text(new CheckRequest(5, true)));
    }

    /** TLC has no action invariant; it checks {@code [][Step]_vars} as an action property. */
    @Test
    void namesTheActionInvariantAsAPropertyWhenTheRequestAsksForIt() {
        assertEquals("SPECIFICATION Spec\nINVARIANT Inv\nPROPERTY StepProperty\n",
                TlcConfiguration.text(new CheckRequest(5, false, true)));
    }
}
