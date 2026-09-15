package io.github.tlaplus.hardening.workflow.tlc;

import tlc2.output.EC;
import tlc2.output.IMessagePrinterRecorder;

/**
 * Follows the messages TLC prints, which say when its initial states are complete and which states
 * its error trace consists of.
 *
 * <p>An error trace lists its states in order. A liveness trace ends either with a stuttering step
 * or with a step back to an earlier state; each adds one transition. A state that violates the
 * invariant or property initially is reported without a trace.
 */
final class TlcMessageMetrics implements IMessagePrinterRecorder {
    private boolean initialStatesComplete;
    private long traceStates;
    private long loopTransitions;

    @Override
    public synchronized void record(int code, Object... objects) {
        switch (code) {
            case EC.TLC_INIT_GENERATED1, EC.TLC_INIT_GENERATED2, EC.TLC_INIT_GENERATED3, EC.TLC_INIT_GENERATED4 ->
                    initialStatesComplete = true;
            case EC.TLC_STATE_PRINT1, EC.TLC_STATE_PRINT2 -> traceStates++;
            case EC.TLC_STATE_PRINT3, EC.TLC_BACK_TO_STATE -> loopTransitions++;
            default -> {
                // Other messages carry nothing measured here.
            }
        }
    }

    synchronized boolean initialStatesComplete() {
        return initialStatesComplete;
    }

    /** Returns the transitions of the printed error trace, zero when TLC printed none. */
    synchronized long traceLength() {
        return traceStates == 0 ? 0 : traceStates - 1 + loopTransitions;
    }
}
