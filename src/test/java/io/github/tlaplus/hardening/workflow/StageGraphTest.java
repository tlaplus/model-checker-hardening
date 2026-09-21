package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import org.junit.jupiter.api.Test;

class StageGraphTest {
    @Test
    void onlyTheStagesThatClaimPerEntryWorkOrderItByGeneration() {
        assertTrue(StageGraph.ordersWorkByGeneration(CorpusStage.PARSER));
        assertTrue(StageGraph.ordersWorkByGeneration(CorpusStage.TLC));
        assertTrue(StageGraph.ordersWorkByGeneration(CorpusStage.APALACHE));
        assertFalse(StageGraph.ordersWorkByGeneration(CorpusStage.AGGREGATOR));
        assertFalse(StageGraph.ordersWorkByGeneration(CorpusStage.QUALITY));
    }
}
