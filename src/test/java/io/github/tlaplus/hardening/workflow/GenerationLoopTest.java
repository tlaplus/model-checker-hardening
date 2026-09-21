package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GenerationLoopTest {
    @Test
    void generationZeroIsPbtAndLaterGenerationsReserveTheConfiguredMutantShare() {
        assertEquals(0, GenerationLoop.mutantTarget(80, 0, 0.5));
        assertEquals(40, GenerationLoop.mutantTarget(80, 1, 0.5));
        assertEquals(0, GenerationLoop.mutantTarget(80, 1, 0.0));
        assertEquals(80, GenerationLoop.mutantTarget(80, 1, 1.0));
    }

    @Test
    void roundsTheMutantShareOfAPartialFinalGeneration() {
        assertEquals(2, GenerationLoop.mutantTarget(3, 1, 0.5));
        assertEquals(1, GenerationLoop.mutantTarget(2, 1, 0.5));
    }
}
