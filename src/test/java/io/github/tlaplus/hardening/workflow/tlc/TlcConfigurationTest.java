package io.github.tlaplus.hardening.workflow.tlc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.workflow.worker.CheckKind;
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
                TlcConfiguration.text(new CheckRequest(5, CheckKind.INVARIANT_AND_PROPERTY)));
    }

    /**
     * A temporal relation's TLC stage runs the relation and then each side's property alone, under
     * that side's specification (ADR 0027 §4).
     */
    @Test
    void namesEachRunOfATemporalRelation() {
        assertEquals("SPECIFICATION Spec\nINVARIANT Inv\nPROPERTY StepProperty\n",
                TlcConfiguration.text(new CheckRequest(5, CheckKind.RELATION_AND_PROPERTIES)));
        assertEquals("SPECIFICATION Spec\nPROPERTY Prop\n",
                TlcConfiguration.text(new CheckRequest(5, CheckKind.EXPLORED_PROPERTY)));
        assertEquals("SPECIFICATION SpecC\nPROPERTY PropC\n",
                TlcConfiguration.text(new CheckRequest(5, CheckKind.CHECKED_PROPERTY)));
    }

    /** TLC has no action invariant; it checks {@code [][Step]_vars} as an action property. */
    @Test
    void namesTheActionInvariantAsAPropertyWhenTheRequestAsksForIt() {
        assertEquals("SPECIFICATION Spec\nINVARIANT Inv\nPROPERTY StepProperty\n",
                TlcConfiguration.text(new CheckRequest(5, CheckKind.RELATION)));
    }
}
