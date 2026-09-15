package io.github.tlaplus.hardening.checker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class ExplorationMetricsTest {
    /** The field names are stored in corpus entries, so renaming one breaks existing corpora. */
    @Test
    void pinsTheStoredFieldNames() {
        assertEquals(
                List.of(
                        "initStates",
                        "distinctStates",
                        "generatedStates",
                        "projectedStates",
                        "depth",
                        "projectedDepth",
                        "actions",
                        "actionsFired",
                        "actionsDiscovering",
                        "maxStateNodes",
                        "maxCardinality",
                        "maxNesting",
                        "traceLength"),
                Arrays.stream(ExplorationCount.values()).map(ExplorationCount::fieldName).toList());
        assertEquals(
                List.of("init", "explore", "complete"),
                Arrays.stream(ExplorationPhase.values()).map(ExplorationPhase::encodedName).toList());
    }

    @Test
    void decodesNamesAndRejectsUnknownPhases() {
        for (var count : ExplorationCount.values()) {
            assertEquals(Optional.of(count), ExplorationCount.fromFieldName(count.fieldName()));
        }
        assertTrue(ExplorationCount.fromFieldName("future").isEmpty());
        for (var phase : ExplorationPhase.values()) {
            assertEquals(phase, ExplorationPhase.fromEncodedName(phase.encodedName()));
        }
        assertThrows(IllegalArgumentException.class, () -> ExplorationPhase.fromEncodedName("done"));
    }

    @Test
    void keepsOnlyTheMeasuredCounts() {
        var metrics = ExplorationMetrics.builder()
                .phase(ExplorationPhase.EXPLORE)
                .count(ExplorationCount.DEPTH, 3)
                .saturated(true)
                .build();

        assertEquals(Optional.of(ExplorationPhase.EXPLORE), metrics.phase());
        assertEquals(OptionalLong.of(3), metrics.count(ExplorationCount.DEPTH));
        assertEquals(OptionalLong.empty(), metrics.count(ExplorationCount.INIT_STATES));
        assertTrue(metrics.saturated());
        assertThrows(
                UnsupportedOperationException.class,
                () -> metrics.counts().put(ExplorationCount.ACTIONS, 1L));
    }

    @Test
    void rejectsNegativeCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExplorationMetrics(
                        Optional.empty(), Map.of(ExplorationCount.DEPTH, -1L), false));
    }
}
