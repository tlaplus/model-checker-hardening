package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tlaplus.hardening.corpus.CorpusStage.PipelineRole;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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

    /**
     * Generation scheduling (ADR 0010) reads a stage's role instead of naming stages: which queues
     * are ordered oldest generation first, which verdicts settle an entry, and which stage runs
     * outside the per-entry pipeline all follow from it. A stage added without a considered role
     * would silently join the wrong group, so the roles are pinned here.
     */
    @Test
    void everyStageDeclaresThePipelineRoleGenerationSchedulingReadsIt() {
        assertEquals(
                Map.of(
                        CorpusStage.PARSER, PipelineRole.PARSING,
                        CorpusStage.TLC, PipelineRole.CHECKING,
                        CorpusStage.APALACHE, PipelineRole.CHECKING,
                        CorpusStage.AGGREGATOR, PipelineRole.AGGREGATION,
                        CorpusStage.QUALITY, PipelineRole.SELECTION),
                Arrays.stream(CorpusStage.values())
                        .collect(Collectors.toMap(stage -> stage, CorpusStage::role)));
    }

    @Test
    void theCheckerBranchesAreExactlyTheStagesThatCheck() {
        assertEquals(List.of(CorpusStage.TLC, CorpusStage.APALACHE), CorpusStage.checkerBranches());
        for (var stage : CorpusStage.values()) {
            assertEquals(
                    stage.role() == PipelineRole.CHECKING,
                    CorpusStage.checkerBranches().contains(stage),
                    stage.displayName());
        }
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
