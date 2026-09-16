package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GenerationLoopTest {
    @Test
    void aFreshGenerationMutatesTheConfiguredShare() {
        assertEquals(40, GenerationLoop.missingMutants(80, 0, 80, 0.5));
        assertEquals(0, GenerationLoop.missingMutants(80, 0, 80, 0.0));
        assertEquals(80, GenerationLoop.missingMutants(80, 0, 80, 1.0));
    }

    @Test
    void aResumedGenerationAdmitsOnlyTheMutantsItStillLacks() {
        // 46 entries were admitted before the interruption, 40 of them mutants.
        assertEquals(0, GenerationLoop.missingMutants(80, 40, 34, 0.5));
        // 46 entries were admitted, 6 of them mutants.
        assertEquals(34, GenerationLoop.missingMutants(80, 6, 34, 0.5));
        // 46 entries were admitted, 30 of them mutants.
        assertEquals(10, GenerationLoop.missingMutants(80, 30, 34, 0.5));
    }
}
