package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RunValueTest {
    @Test
    void boundedShowsTheCountAgainstItsLimitAndMovesWithTheCount() {
        var entries = new RunValue.Bounded(90_500, 100_000);
        assertEquals("90500/100000", entries.format(Precision.EXACT));
        assertEquals(HumanNumber.compact(90_500) + "/" + HumanNumber.compact(100_000), entries.format(Precision.COMPACT));
        assertTrue(entries.directional());
        assertEquals(RunValue.Direction.UP, entries.since(new RunValue.Bounded(90_000, 100_000)));
        assertEquals(RunValue.Direction.DOWN, entries.since(new RunValue.Bounded(91_000, 100_000)));
    }
}
