package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusStage.PipelineRole;
import io.github.tlaplus.hardening.gen.InputKind;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CorpusStageTest {
    /**
     * The graph oracle requires a shortest counterexample only from these checkers (ADR 0023 §5);
     * {@code GraphModuleCheckersTest} measures it on the checkers themselves.
     */
    @Test
    void pinsWhichCheckersReportAShortestCounterexample() {
        var search = Arrays.stream(CorpusStage.values())
                .collect(Collectors.toMap(CorpusStage::metadataName, CorpusStage::counterexampleSearch));
        assertEquals(Map.of(
                "parser", CorpusStage.CounterexampleSearch.ANY,
                "tlc", CorpusStage.CounterexampleSearch.SHORTEST,
                "tlcn", CorpusStage.CounterexampleSearch.ANY,
                "apalache", CorpusStage.CounterexampleSearch.SHORTEST,
                "aggregator", CorpusStage.CounterexampleSearch.ANY,
                "quality", CorpusStage.CounterexampleSearch.ANY), search);
    }

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
                        CorpusStage.TLCN,
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
                        CorpusStage.TLCN, PipelineRole.CHECKING,
                        CorpusStage.APALACHE, PipelineRole.CHECKING,
                        CorpusStage.AGGREGATOR, PipelineRole.AGGREGATION,
                        CorpusStage.QUALITY, PipelineRole.SELECTION),
                Arrays.stream(CorpusStage.values())
                        .collect(Collectors.toMap(stage -> stage, CorpusStage::role)));
    }

    @Test
    void theCheckerBranchesAreExactlyTheStagesThatCheck() {
        assertEquals(List.of(CorpusStage.TLC, CorpusStage.TLCN, CorpusStage.APALACHE), CorpusStage.checkerBranches());
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
    /** Pins the result directories whose entries no later stage reads (ADR 0031 §1). */
    @Test
    void pinsTheVerdictsThatRetireAnEntry() {
        var retiring = new TreeMap<String, List<CorpusVerdict>>();
        for (var stage : CorpusStage.values()) {
            retiring.put(stage.metadataName(), stage.resultVerdicts().stream()
                    .filter(stage.role()::retires)
                    .toList());
        }
        assertEquals(Map.of(
                "parser", List.of(CorpusVerdict.FAIL, CorpusVerdict.CRASH),
                "tlc", List.of(CorpusVerdict.CRASH),
                "tlcn", List.of(CorpusVerdict.CRASH),
                "apalache", List.of(CorpusVerdict.CRASH),
                "aggregator", List.of(CorpusVerdict.FAIL),
                "quality", List.of(CorpusVerdict.FAIL)), retiring);
    }

    @Test
    void retiresAnEntryWhenAnyRecordedVerdictRetiresIt() {
        var input = new CorpusInput(InputKind.EXPRESSION, new byte[] {1});
        var time = Instant.ofEpochSecond(1);
        var parserPass = new StageMetadata("parser", CorpusVerdict.PASS, time, time);
        var tlcPass = new StageMetadata("tlc", CorpusVerdict.PASS, time, time);
        var aggregated = new StageMetadata("aggregator", CorpusVerdict.PASS, time, time);

        assertFalse(new CorpusEnvelope(input, Optional.empty(), List.of()).isRetired());
        assertFalse(new CorpusEnvelope(input, Optional.empty(), List.of(parserPass, tlcPass)).isRetired());
        assertTrue(new CorpusEnvelope(input, Optional.empty(), List.of(
                parserPass, new StageMetadata("tlc", CorpusVerdict.CRASH, time, time))).isRetired());
        assertFalse(new CorpusEnvelope(input, Optional.empty(), List.of(parserPass, tlcPass, aggregated,
                new StageMetadata("quality", CorpusVerdict.PASS, time, time))).isRetired());
        assertTrue(new CorpusEnvelope(input, Optional.empty(), List.of(parserPass, tlcPass, aggregated,
                new StageMetadata("quality", CorpusVerdict.FAIL, time, time))).isRetired());
    }
}
