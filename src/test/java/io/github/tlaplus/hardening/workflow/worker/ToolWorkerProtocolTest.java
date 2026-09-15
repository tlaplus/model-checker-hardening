package io.github.tlaplus.hardening.workflow.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ToolWorkerProtocolTest {
    @Test
    void roundTripsResultsWithAndWithoutExplorationMetrics() throws Exception {
        var metrics = ExplorationMetrics.builder()
                .phase(ExplorationPhase.INIT)
                .count(ExplorationCount.INIT_STATES, 0)
                .count(ExplorationCount.MAX_STATE_NODES, 100_000)
                .saturated(true)
                .build();
        var counterexample = ToolResult.counterexample("violated").withMetrics(
                ExplorationMetrics.builder().count(ExplorationCount.TRACE_LENGTH, 4).build());
        var failure = ToolResult.failure(CheckerFailureCode.SPEC_EVAL, "undefined").withMetrics(metrics);
        var plain = new ToolResult(StageOutcome.PASS, "accepted");

        for (var result : new ToolResult[] {counterexample, failure, plain}) {
            assertEquals(result, roundTrip(result));
        }
    }

    @Test
    void rejectsAnUnknownMetricName() throws Exception {
        var frame = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(frame)) {
            output.writeInt(StageOutcome.PASS.protocolCode());
            output.writeInt(ToolWorkerProtocol.NO_FAILURE_CODE);
            output.writeInt(0);
            output.writeBoolean(true);
            output.writeUTF("");
            output.writeBoolean(false);
            output.writeInt(1);
            output.writeUTF("futureMetric");
            output.writeLong(1);
        }

        var failure = assertThrows(
                WorkflowException.class,
                () -> ToolWorkerProtocol.readResult(
                        new DataInputStream(new ByteArrayInputStream(frame.toByteArray()))));

        assertTrue(failure.getCause().getMessage().contains("futureMetric"));
    }

    @Test
    void refusesMetricsOnACrash() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ToolResult(
                        StageOutcome.CRASH,
                        Optional.empty(),
                        "crash",
                        Optional.of(ExplorationMetrics.builder().build())));
    }

    private static ToolResult roundTrip(ToolResult result) throws IOException, WorkflowException {
        var frame = new ByteArrayOutputStream();
        ToolWorkerProtocol.writeResult(new DataOutputStream(frame), result);
        return ToolWorkerProtocol.readResult(
                new DataInputStream(new ByteArrayInputStream(frame.toByteArray())));
    }
}
