package io.github.tlaplus.hardening.workflow.tlc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import io.github.tlaplus.hardening.workflow.worker.CheckKind;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** One test per row of the fold table of ADR 0027 §4. */
class PropertyRunsVerdictTest {
    private static final ExplorationMetrics METRICS =
            ExplorationMetrics.builder().phase(ExplorationPhase.COMPLETE).build();
    private static final ToolResult PASS = new ToolResult(StageOutcome.PASS, "relation passed").withMetrics(METRICS);
    private static final ToolResult HOLDS = new ToolResult(StageOutcome.PASS, "property holds");
    private static final ToolResult VIOLATED = ToolResult.counterexample("Error: Temporal property Prop was violated.");
    private static final ToolResult FAILED = ToolResult.failure(CheckerFailureCode.SPEC_EVAL, "Error: CASE");
    private static final ToolResult CRASHED = new ToolResult(StageOutcome.CRASH, "TLC worker timed out");

    @Test
    void aRelationCounterexampleRunsNoProperty() throws Exception {
        var relation = ToolResult.counterexample("Action property StepProperty is violated.");
        var runs = new Runs(Map.of());
        assertEquals(relation, PropertyRunsVerdict.fold(relation, runs));
        assertEquals(List.of(), runs.started);
    }

    @Test
    void equalVerdictsPass() throws Exception {
        for (var verdict : List.of(HOLDS, VIOLATED)) {
            var runs = new Runs(Map.of(CheckKind.EXPLORED_PROPERTY, verdict, CheckKind.CHECKED_PROPERTY, verdict));
            assertEquals(PASS, PropertyRunsVerdict.fold(PASS, runs), "both sides " + verdict.outcome());
            assertEquals(CheckKind.PROPERTY_RUNS, runs.started);
        }
    }

    @Test
    void differentVerdictsAreACounterexampleWithTheRelationsMetrics() throws Exception {
        var runs = new Runs(Map.of(CheckKind.EXPLORED_PROPERTY, HOLDS, CheckKind.CHECKED_PROPERTY, VIOLATED));
        var result = PropertyRunsVerdict.fold(PASS, runs);
        assertEquals(StageOutcome.COUNTEREXAMPLE, result.outcome());
        assertTrue(result.diagnostic().startsWith(
                "property verdicts differ (ADR 0027 §4): Spec |= Prop holds, SpecC |= PropC is violated"),
                result.diagnostic());
        assertTrue(result.diagnostic().contains("Temporal property Prop was violated"), result.diagnostic());
        assertEquals(Optional.of(METRICS), result.metrics());
    }

    /** The property runs still run after a relation run that failed, and may differ. */
    @Test
    void differentVerdictsOutweighAFailedRelation() throws Exception {
        var runs = new Runs(Map.of(CheckKind.EXPLORED_PROPERTY, VIOLATED, CheckKind.CHECKED_PROPERTY, HOLDS));
        assertEquals(StageOutcome.COUNTEREXAMPLE, PropertyRunsVerdict.fold(FAILED, runs).outcome());
    }

    @Test
    void anUndecidedExploredSideIsTheOutcomeOfAPassingRelation() throws Exception {
        var runs = new Runs(Map.of(CheckKind.EXPLORED_PROPERTY, FAILED));
        var result = PropertyRunsVerdict.fold(PASS, runs);
        assertEquals(StageOutcome.FAIL, result.outcome());
        assertEquals(Optional.of(CheckerFailureCode.SPEC_EVAL), result.failureCode());
        assertTrue(result.diagnostic().startsWith("in the property run Spec |= Prop (ADR 0027 §4):\nError: CASE"),
                result.diagnostic());
        assertEquals(Optional.of(METRICS), result.metrics());
        assertEquals(List.of(CheckKind.EXPLORED_PROPERTY), runs.started, "the checked side waits for a verdict");
    }

    @Test
    void anUndecidedCheckedSideIsTheOutcomeOfAPassingRelation() throws Exception {
        var runs = new Runs(Map.of(CheckKind.EXPLORED_PROPERTY, HOLDS, CheckKind.CHECKED_PROPERTY, CRASHED));
        var result = PropertyRunsVerdict.fold(PASS, runs);
        assertEquals(StageOutcome.CRASH, result.outcome());
        assertTrue(result.diagnostic().startsWith("in the property run SpecC |= PropC"), result.diagnostic());
        assertEquals(Optional.empty(), result.metrics(), "a crash carries no metrics");
    }

    @Test
    void aRelationThatDidNotPassIsTheOutcomeOtherwise() throws Exception {
        var undecided = new Runs(Map.of(CheckKind.EXPLORED_PROPERTY, FAILED));
        assertEquals(FAILED, PropertyRunsVerdict.fold(FAILED, undecided));
        var equal = new Runs(Map.of(CheckKind.EXPLORED_PROPERTY, HOLDS, CheckKind.CHECKED_PROPERTY, HOLDS));
        assertEquals(CRASHED, PropertyRunsVerdict.fold(CRASHED, equal));
    }

    /** Property runs with fixed results, which records the runs the fold starts. */
    private static final class Runs implements PropertyRunsVerdict.PropertyRun {
        private final Map<CheckKind, ToolResult> results;
        private final List<CheckKind> started = new ArrayList<>();

        Runs(Map<CheckKind, ToolResult> results) {
            this.results = results;
        }

        @Override
        public ToolResult run(CheckKind kind) {
            started.add(kind);
            var result = results.get(kind);
            if (result == null) {
                throw new AssertionError("unexpected property run " + kind);
            }
            return result;
        }
    }
}
