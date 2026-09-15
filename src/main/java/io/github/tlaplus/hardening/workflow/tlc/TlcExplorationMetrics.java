package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.util.Objects;
import tlc2.TLC;
import tlc2.TLCGlobals;
import tlc2.output.MP;

/**
 * Measures one TLC run and attaches the result to its classified outcome (ADR 0008).
 *
 * <p>A TLC worker checks one input, so one instance observes one run: {@link #install} registers
 * the observers before {@link TLC#process()}, and {@link #attach} reads them afterwards.
 */
final class TlcExplorationMetrics {
    /** Value nodes measured per state before the size walk stops. */
    static final long STATE_NODE_CAP = 100_000;

    private final TlcStateMetrics states;
    private final TlcMessageMetrics messages = new TlcMessageMetrics();

    TlcExplorationMetrics() {
        this(STATE_NODE_CAP);
    }

    TlcExplorationMetrics(long stateNodeCap) {
        states = new TlcStateMetrics(GeneratedSpec.STEP_VARIABLE, stateNodeCap);
    }

    /** Registers the observers with {@code tlc}, whose parameters must already be handled. */
    void install(TLC tlc) {
        tlc.setStateWriter(states);
        MP.setRecorder(messages);
    }

    /** Returns {@code result} with the metrics of the observed run, unless the run crashed. */
    ToolResult attach(ToolResult result) {
        Objects.requireNonNull(result, "result");
        if (result.outcome() == StageOutcome.CRASH) {
            return result;
        }
        var metrics = ExplorationMetrics.builder().phase(phase(result.outcome()));
        states.addTo(metrics);
        var checker = TLCGlobals.mainChecker;
        if (checker != null) {
            metrics.count(ExplorationCount.ACTIONS, checker.tool.getActions().length);
        }
        if (result.outcome() == StageOutcome.COUNTEREXAMPLE) {
            metrics.count(ExplorationCount.TRACE_LENGTH, messages.traceLength());
        }
        return result.withMetrics(metrics.build());
    }

    private ExplorationPhase phase(StageOutcome outcome) {
        if (outcome == StageOutcome.PASS) {
            return ExplorationPhase.COMPLETE;
        }
        return messages.initialStatesComplete() ? ExplorationPhase.EXPLORE : ExplorationPhase.INIT;
    }
}
