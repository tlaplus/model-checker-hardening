package io.github.tlaplus.hardening.workflow.tlc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class TlcCommandLineMetricsTest {
    /** The tail of a liveness run: a progress report, then the final statistics. */
    private static final String PASS_OUTPUT = """
            Progress(3) at 2026-11-25 10:58:01: 3 states generated, 3 distinct states found, 0 states left on queue.
            Checking temporal properties for the complete state space with 3 total distinct states at (2026-11-25 10:58:01)
            Model checking completed. No error has been found.
            7 states generated, 5 distinct states found, 0 states left on queue.
            The depth of the complete state graph search is 4.
            """;

    @Test
    void readsTheLastStatisticsOfAPass() {
        var result = TlcCommandLineMetrics.attach(new ToolResult(StageOutcome.PASS, PASS_OUTPUT));

        var metrics = result.metrics().orElseThrow();
        assertEquals(Optional.of(ExplorationPhase.COMPLETE), metrics.phase());
        assertEquals(OptionalLong.of(5), metrics.count(ExplorationCount.DISTINCT_STATES));
        assertEquals(OptionalLong.empty(), metrics.count(ExplorationCount.GENERATED_STATES));
    }

    @Test
    void readsASingleState() {
        var result = TlcCommandLineMetrics.attach(new ToolResult(StageOutcome.PASS,
                "1 states generated, 1 distinct states found, 0 states left on queue.\n"));

        assertEquals(OptionalLong.of(1),
                result.metrics().orElseThrow().count(ExplorationCount.DISTINCT_STATES));
    }

    /** A counterexample or a failure stopped the search, so its count depends on the search order. */
    @Test
    void recordsNothingForAnIncompleteSearch() {
        var stopped = "2 states generated, 2 distinct states found, 1 states left on queue.\n";

        assertEquals(Optional.empty(),
                TlcCommandLineMetrics.attach(ToolResult.counterexample(stopped)).metrics());
        assertEquals(Optional.empty(),
                TlcCommandLineMetrics.attach(ToolResult.failure(CheckerFailureCode.SPEC_EVAL, stopped)).metrics());
        assertEquals(Optional.empty(),
                TlcCommandLineMetrics.attach(new ToolResult(StageOutcome.PASS, stopped)).metrics());
    }

    @Test
    void recordsNothingWithoutStatistics() {
        assertEquals(Optional.empty(),
                TlcCommandLineMetrics.attach(new ToolResult(StageOutcome.PASS, "No error has been found.\n")).metrics());
    }
}
