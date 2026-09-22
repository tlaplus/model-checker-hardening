package io.github.tlaplus.hardening.workflow.tlc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs real TLC on small modules in the generated skeleton and checks what the worker measured. */
class TlcExplorationMetricsTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final CheckerStageConfig CONFIG = new CheckerStageConfig(10, 20, 256, 1);

    @TempDir
    Path directory;

    @Test
    void measuresAVacuousPass() throws Exception {
        var result = check(module("x \\in {}", "x' = x", "TRUE", "TRUE"), false);

        assertEquals(StageOutcome.PASS, result.outcome(), result.diagnostic());
        var metrics = metrics(result);
        assertEquals(Optional.of(ExplorationPhase.COMPLETE), metrics.phase());
        assertCounts(metrics, Map.of(
                ExplorationCount.INIT_STATES, 0L,
                ExplorationCount.DISTINCT_STATES, 0L,
                ExplorationCount.PROJECTED_STATES, 0L,
                ExplorationCount.ACTIONS_FIRED, 0L));
        assertEquals(OptionalLong.empty(), metrics.count(ExplorationCount.TRACE_LENGTH));
    }

    @Test
    void projectsAwayTheStepCounter() throws Exception {
        var result = check(module("x = 0", "x' = x", "TRUE", "TRUE"), false);

        assertEquals(StageOutcome.PASS, result.outcome(), result.diagnostic());
        // step = 0..3; every state has a generated successor but the last, and a stuttering one.
        assertCounts(metrics(result), Map.of(
                ExplorationCount.INIT_STATES, 1L,
                ExplorationCount.DISTINCT_STATES, 4L,
                ExplorationCount.GENERATED_STATES, 8L,
                ExplorationCount.PROJECTED_STATES, 1L,
                ExplorationCount.DEPTH, 3L,
                ExplorationCount.PROJECTED_DEPTH, 0L,
                ExplorationCount.ACTIONS, 2L,
                ExplorationCount.ACTIONS_FIRED, 2L,
                ExplorationCount.ACTIONS_DISCOVERING, 1L,
                ExplorationCount.MAX_NESTING, 0L));
    }

    @Test
    void countsDisjunctsBySourceLocationRatherThanByBinding() throws Exception {
        var source = """
                ---- MODULE FuzzInput ----
                EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants
                VARIABLES x, step
                vars == <<x, step>>
                Init == x = 0 /\\ step = 0
                Next == \\/ step < 3 /\\ \\E d \\in {1, 2} : x' = x + d /\\ step' = step + 1
                        \\/ step < 3 /\\ x' = x /\\ step' = step + 1
                        \\/ UNCHANGED vars
                Inv == TRUE
                Fairness == TRUE
                Spec == Init /\\ [][Next]_vars /\\ Fairness
                Prop == TRUE
                Liveness == Fairness => Prop
                ====
                """;
        var result = check(source, false);

        assertEquals(StageOutcome.PASS, result.outcome(), result.diagnostic());
        assertCounts(metrics(result), Map.of(
                ExplorationCount.ACTIONS_FIRED, 3L,
                ExplorationCount.ACTIONS_DISCOVERING, 2L,
                ExplorationCount.PROJECTED_STATES, 7L,
                ExplorationCount.PROJECTED_DEPTH, 3L));
    }

    @Test
    void measuresAnInitialStateViolation() throws Exception {
        var result = check(module("x = 0", "x' = x", "x # 0", "TRUE"), false);

        assertEquals(StageOutcome.COUNTEREXAMPLE, result.outcome(), result.diagnostic());
        var metrics = metrics(result);
        assertEquals(Optional.of(ExplorationPhase.INIT), metrics.phase());
        assertCounts(metrics, Map.of(
                ExplorationCount.INIT_STATES, 1L,
                ExplorationCount.DEPTH, 0L,
                ExplorationCount.TRACE_LENGTH, 0L));
    }

    @Test
    void measuresAViolationAfterTwoSteps() throws Exception {
        var result = check(module("x = 0", "x' = x + 1", "x < 2", "TRUE"), false);

        assertEquals(StageOutcome.COUNTEREXAMPLE, result.outcome(), result.diagnostic());
        var metrics = metrics(result);
        assertEquals(Optional.of(ExplorationPhase.EXPLORE), metrics.phase());
        assertCounts(metrics, Map.of(
                ExplorationCount.DEPTH, 2L,
                ExplorationCount.TRACE_LENGTH, 2L));
    }

    @Test
    void countsTheLoopOfALivenessCounterexample() throws Exception {
        var result = check(module("x = 0", "x' = x + 1", "TRUE", "<>(x = 9)"), true);

        assertEquals(StageOutcome.COUNTEREXAMPLE, result.outcome(), result.diagnostic());
        var metrics = metrics(result);
        assertEquals(Optional.of(ExplorationPhase.EXPLORE), metrics.phase());
        // x = step = 0..3, then stuttering forever: three steps and the stuttering loop.
        assertEquals(OptionalLong.of(4), metrics.count(ExplorationCount.TRACE_LENGTH));
    }

    @Test
    void measuresAFailureBeforeTheInitialStatesAreComplete() throws Exception {
        var result = check(module("x = Head(<<>>)", "x' = x", "TRUE", "TRUE"), false);

        assertEquals(StageOutcome.FAIL, result.outcome(), result.diagnostic());
        var metrics = metrics(result);
        assertEquals(Optional.of(ExplorationPhase.INIT), metrics.phase());
        assertCounts(metrics, Map.of(ExplorationCount.INIT_STATES, 0L));
        assertEquals(OptionalLong.empty(), metrics.count(ExplorationCount.TRACE_LENGTH));
    }

    @Test
    void measuresTheShapeOfStateValues() throws Exception {
        var result = check(module("x = [a |-> {1, 2}, b |-> <<3>>]", "x' = x", "TRUE", "TRUE"), false);

        assertEquals(StageOutcome.PASS, result.outcome(), result.diagnostic());
        // The record, its set, 1, 2, its tuple, 3, and the step counter.
        assertCounts(metrics(result), Map.of(
                ExplorationCount.MAX_STATE_NODES, 7L,
                ExplorationCount.MAX_CARDINALITY, 2L,
                ExplorationCount.MAX_NESTING, 2L));
    }

    @Test
    void projectsNothingAwayInAnExpressionModule() throws Exception {
        var source = """
                ---- MODULE FuzzInput ----
                EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants
                VARIABLE exprValue
                Init == exprValue = 1
                Next == UNCHANGED exprValue
                Inv == exprValue = 1
                Fairness == TRUE
                Spec == Init /\\ [][Next]_exprValue /\\ Fairness
                Prop == TRUE
                Liveness == Fairness => Prop
                ====
                """;
        var result = check(source, false);

        assertEquals(StageOutcome.PASS, result.outcome(), result.diagnostic());
        assertCounts(metrics(result), Map.of(
                ExplorationCount.DISTINCT_STATES, 1L,
                ExplorationCount.PROJECTED_STATES, 1L));
    }

    private ToolResult check(String source, boolean temporalProperty) throws Exception {
        var scratch = Files.createTempDirectory(directory, "scratch");
        return TlcProcess.check(scratch, List.of(), new ToolInput(source, new CheckRequest(3, temporalProperty)), CONFIG, TIMEOUT);
    }

    private static ExplorationMetrics metrics(ToolResult result) {
        assertTrue(result.metrics().isPresent(), result.diagnostic());
        return result.metrics().get();
    }

    private static void assertCounts(ExplorationMetrics metrics, Map<ExplorationCount, Long> expected) {
        expected.forEach((count, value) ->
                assertEquals(OptionalLong.of(value), metrics.count(count), count.fieldName()));
    }

    /** A module in the generated skeleton: one variable and a step counter bounded by 3. */
    private static String module(String init, String next, String invariant, String property) {
        return """
                ---- MODULE FuzzInput ----
                EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants
                VARIABLES x, step
                vars == <<x, step>>
                Init == %s /\\ step = 0
                Next == \\/ (step < 3 /\\ (%s) /\\ step' = step + 1)
                        \\/ UNCHANGED vars
                Inv == %s
                Fairness == TRUE
                Spec == Init /\\ [][Next]_vars /\\ Fairness
                Prop == %s
                Liveness == Fairness => Prop
                ====
                """.formatted(init, next, invariant, property);
    }
}
