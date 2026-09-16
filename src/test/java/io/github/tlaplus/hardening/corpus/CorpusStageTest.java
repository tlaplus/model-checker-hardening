package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorpusStageTest {
    @Test
    void meaningsGiveVerdictsTheirStageSpecificLabel() {
        for (var stage : CorpusStage.capacityLimitedStages()) {
            assertEquals("Pass", stage.meaning(CorpusVerdict.PASS).label());
            assertEquals("Fail", stage.meaning(CorpusVerdict.FAIL).label());
            assertEquals("Crash", stage.meaning(CorpusVerdict.CRASH).label());
        }
        for (var checker : CorpusStage.checkerBranches()) {
            assertEquals("Cex", checker.meaning(CorpusVerdict.COUNTEREXAMPLE).label());
        }
        assertEquals("Agree", CorpusStage.AGGREGATOR.meaning(CorpusVerdict.PASS).label());
        assertEquals("Differ", CorpusStage.AGGREGATOR.meaning(CorpusVerdict.FAIL).label());
        assertEquals("Keep", CorpusStage.QUALITY.meaning(CorpusVerdict.PASS).label());
        assertEquals("Drop", CorpusStage.QUALITY.meaning(CorpusVerdict.FAIL).label());
    }

    @Test
    void everyStageGivesMeaningsToExactlyItsRecordedVerdicts() {
        for (var stage : CorpusStage.values()) {
            for (var verdict : CorpusVerdict.values()) {
                if (stage.resultVerdicts().contains(verdict)) {
                    assertNotNull(stage.meaning(verdict));
                } else {
                    assertThrows(IllegalArgumentException.class, () -> stage.meaning(verdict));
                }
            }
        }
    }

    @Test
    void declarationOrderIsPipelineOrder() {
        assertEquals(
                List.of(
                        CorpusStage.PARSER,
                        CorpusStage.TLC,
                        CorpusStage.APALACHE,
                        CorpusStage.AGGREGATOR,
                        CorpusStage.QUALITY),
                List.of(CorpusStage.values()));
    }

    @Test
    void onlyCheckerStagesRecordCounterexamples() {
        assertEquals(
                List.of(CorpusVerdict.PASS, CorpusVerdict.FAIL, CorpusVerdict.CRASH),
                CorpusStage.PARSER.resultVerdicts());
        assertEquals(
                List.of(
                        CorpusVerdict.PASS,
                        CorpusVerdict.COUNTEREXAMPLE,
                        CorpusVerdict.FAIL,
                        CorpusVerdict.CRASH),
                CorpusStage.TLC.resultVerdicts());
        assertEquals(
                CorpusStage.TLC.resultVerdicts(),
                CorpusStage.APALACHE.resultVerdicts());
        assertEquals(
                List.of(CorpusVerdict.PASS, CorpusVerdict.FAIL),
                CorpusStage.AGGREGATOR.resultVerdicts());
        assertEquals(
                CorpusStage.AGGREGATOR.resultVerdicts(),
                CorpusStage.QUALITY.resultVerdicts());
    }
}
