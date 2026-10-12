package io.github.tlaplus.hardening.workflow.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Reads the weight annotation of a rule from its comments, without SANY. */
class RuleModuleDefinitionsTest {
    @Test
    void anUnannotatedRuleHasNoWeight() throws Exception {
        assertEquals(OptionalInt.empty(), RuleModuleDefinitions.weight("R", new String[] {}));
        assertEquals(OptionalInt.empty(), RuleModuleDefinitions.weight("R", new String[] {"\\* @type: (Int) => Bool;"}));
    }

    @Test
    void readsTheWeightBesideOtherAnnotations() throws Exception {
        assertEquals(OptionalInt.of(8), RuleModuleDefinitions.weight("R",
                new String[] {"\\* @type: (Int) => Bool;", "\\* @weight: 8;"}));
        assertEquals(OptionalInt.of(0), RuleModuleDefinitions.weight("R",
                new String[] {"(* @type: (Int) => Bool; @weight:0 ; *)"}));
    }

    @Test
    void rejectsAMalformedWeight() {
        for (var comment : new String[] {"\\* @weight: x;", "\\* @weight: 8", "\\* @weight 8;", "\\* @weight: -1;"}) {
            var failure = assertThrows(WorkflowException.class,
                    () -> RuleModuleDefinitions.weight("R", new String[] {comment}), comment);
            assertTrue(failure.getMessage().startsWith("rewrite rule R: malformed annotation"), failure.getMessage());
        }
    }

    @Test
    void rejectsTwoWeights() {
        var failure = assertThrows(WorkflowException.class,
                () -> RuleModuleDefinitions.weight("R", new String[] {"\\* @weight: 1;", "\\* @weight: 2;"}));
        assertTrue(failure.getMessage().contains("more than one @weight"), failure.getMessage());
    }

    @Test
    void anotherAnnotationIsNoWeight() throws Exception {
        assertEquals(OptionalInt.empty(), RuleModuleDefinitions.weight("R", new String[] {"\\* @weights: 2;"}));
    }
}
