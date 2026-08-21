package io.github.tlaplus.hardening.workflow.execution;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OccupancyGateTest {
    @Test
    void aggregationReleasesAResultSlot() {
        var gate = new OccupancyGate(1, 1);

        assertFalse(gate.reserve());
        gate.release();
        assertTrue(gate.reserve());
    }

    @Test
    void refusesToReleaseAnUnoccupiedSlot() {
        var gate = new OccupancyGate(0, 1);

        assertThrows(IllegalStateException.class, gate::release);
    }
}
