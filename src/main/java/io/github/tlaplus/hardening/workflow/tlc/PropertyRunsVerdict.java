package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.worker.CheckKind;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.util.Objects;

/**
 * Folds the relation run of a metamorphic module and its two property runs into the one outcome
 * of the TLC stage (ADR 0027 §4). If the rules are sound, {@code SpecE ⊨ PropE} and {@code SpecC ⊨
 * PropC} reach the same verdict, whether the base property holds or not:
 *
 * <ul>
 *   <li>a counterexample of the relation run is the outcome, and no property run starts;
 *   <li>property runs that both decide, with different verdicts, are a counterexample;
 *   <li>otherwise, a relation run that did not pass is the outcome;
 *   <li>otherwise, a property run that did not decide is the outcome, the explored side's first;
 *   <li>otherwise, the relation run's pass is.
 * </ul>
 *
 * <p>The checked side runs only once the explored side decided. Exploration metrics are always the
 * relation run's, which explores the module the quality gate ranks.
 */
final class PropertyRunsVerdict {
    /** Starts one property run of the module. */
    @FunctionalInterface
    interface PropertyRun {
        ToolResult run(CheckKind kind) throws WorkflowException, InterruptedException;
    }

    private PropertyRunsVerdict() {}

    static ToolResult fold(ToolResult relation, PropertyRun runs) throws WorkflowException, InterruptedException {
        Objects.requireNonNull(relation, "relation");
        Objects.requireNonNull(runs, "runs");
        if (relation.outcome() == StageOutcome.COUNTEREXAMPLE) {
            return relation;
        }
        var explored = runs.run(CheckKind.EXPLORED_PROPERTY);
        if (!decided(explored)) {
            return relation.outcome() == StageOutcome.PASS
                    ? reported(explored, CheckKind.EXPLORED_PROPERTY, relation)
                    : relation;
        }
        var checked = runs.run(CheckKind.CHECKED_PROPERTY);
        if (decided(checked) && checked.outcome() != explored.outcome()) {
            return withRelationMetrics(ToolResult.counterexample(
                    "property verdicts differ (ADR 0027 §4): " + run(CheckKind.EXPLORED_PROPERTY) + " "
                            + verdict(explored) + ", " + run(CheckKind.CHECKED_PROPERTY) + " " + verdict(checked)
                            + "\n" + section(explored, CheckKind.EXPLORED_PROPERTY)
                            + "\n" + section(checked, CheckKind.CHECKED_PROPERTY)), relation);
        }
        if (relation.outcome() != StageOutcome.PASS) {
            return relation;
        }
        return decided(checked) ? relation : reported(checked, CheckKind.CHECKED_PROPERTY, relation);
    }

    /** Whether a run reached a verdict about its property. */
    private static boolean decided(ToolResult result) {
        return result.outcome() == StageOutcome.PASS || result.outcome() == StageOutcome.COUNTEREXAMPLE;
    }

    /** Returns a property run's outcome as the stage's, with the relation run's metrics. */
    private static ToolResult reported(ToolResult result, CheckKind kind, ToolResult relation) {
        return withRelationMetrics(new ToolResult(result.outcome(), result.failureCode(),
                "in the property run " + run(kind) + " (ADR 0027 §4):\n" + result.diagnostic()), relation);
    }

    private static ToolResult withRelationMetrics(ToolResult result, ToolResult relation) {
        if (result.outcome() == StageOutcome.CRASH) {
            return result;
        }
        return new ToolResult(result.outcome(), result.failureCode(), result.diagnostic(),
                relation.metrics());
    }

    private static String section(ToolResult result, CheckKind kind) {
        return "--- " + run(kind) + " ---\n" + result.diagnostic();
    }

    private static String verdict(ToolResult result) {
        return result.outcome() == StageOutcome.PASS ? "holds" : "is violated";
    }

    /** Names a property run by the entry points it checks, as {@code SpecC |= PropC}. */
    private static String run(CheckKind kind) {
        return FuzzInputModule.specification(kind.side()) + " |= " + FuzzInputModule.property(kind.side());
    }
}
