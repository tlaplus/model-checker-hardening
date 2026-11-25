package io.github.tlaplus.hardening.workflow.tlc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import tlc2.output.EC;
import tlc2.output.IMessagePrinterRecorder;
import tlc2.tool.TLCState;
import tlc2.tool.TLCStateInfo;

/**
 * Records the counterexample TLC prints (ADR 0034 §5), as the JSON a worker returns: its states in
 * the Informal Trace Format and what its last state claims.
 *
 * <p>TLC reports a violation, then the trace state by state (codes 2216 and 2217), and for a lasso
 * either a step back to an earlier state (2122) or a stuttering step (2218). A lasso is returned
 * ending in a copy of its loop state. A violation in an initial state, of the invariant (2107) or of
 * a property (2108), comes without a trace:
 * TLC writes every initial state before it checks the invariant, so the violating state is the last
 * initial state written, which {@code lastInitialState} supplies.
 *
 * <p>The last state claims to violate {@code Inv} when TLC names it; a property TLC checks as an
 * invariant names a conjunct instead, and its trace claims only reachability.
 */
final class TlcTraceRecorder implements IMessagePrinterRecorder {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final Supplier<Optional<TLCState>> lastInitialState;
    private final List<JsonNode> states = new ArrayList<>();
    private String violated;
    private boolean initialViolation;
    private int loop = -1;
    private boolean stuttering;
    private boolean unrecordable;

    /** @param lastInitialState the last initial state TLC wrote, empty before the first */
    TlcTraceRecorder(Supplier<Optional<TLCState>> lastInitialState) {
        this.lastInitialState = lastInitialState;
    }

    @Override
    public synchronized void record(int code, Object... objects) {
        try {
            switch (code) {
                case EC.TLC_INVARIANT_VIOLATED_INITIAL, EC.TLC_PROPERTY_VIOLATED_INITIAL -> {
                    violated = String.valueOf(objects[0]);
                    initialViolation = true;
                    lastInitialState.get().ifPresentOrElse(
                            state -> states.add(TlcItfValues.state(state)), () -> unrecordable = true);
                }
                case EC.TLC_INVARIANT_VIOLATED_BEHAVIOR -> violated = String.valueOf(objects[0]);
                case EC.TLC_STATE_PRINT1, EC.TLC_STATE_PRINT2 -> {
                    if (!initialViolation && objects.length > 0 && objects[0] instanceof TLCStateInfo info
                            && info.state != null) {
                        states.add(TlcItfValues.state(info.state));
                    }
                }
                case EC.TLC_BACK_TO_STATE -> loop = Integer.parseInt(String.valueOf(objects[0]).strip()) - 1;
                case EC.TLC_STATE_PRINT3 -> stuttering = true;
                default -> {
                    // Other messages are not part of the trace.
                }
            }
        } catch (RuntimeException exception) {
            // A value the trace format cannot hold, or a message of an unexpected shape: the
            // counterexample is reported without a trace, which changes no verdict.
            unrecordable = true;
        }
    }

    /** Returns the recorded trace as the JSON of ADR 0034 §5, or empty when there is none. */
    synchronized Optional<String> trace() {
        if (unrecordable || states.isEmpty()) {
            return Optional.empty();
        }
        var root = NODES.objectNode();
        var array = NODES.arrayNode();
        states.forEach(array::add);
        if (stuttering) {
            root.put("ending", "loop").put("loop", states.size() - 1);
            array.add(states.getLast().deepCopy());
        } else if (loop >= 0 && loop < states.size()) {
            root.put("ending", "loop").put("loop", loop);
            array.add(states.get(loop).deepCopy());
        } else {
            root.put("ending", FuzzInputModule.INV.equals(violated) ? "invariant" : "prefix");
        }
        root.set("states", array);
        try {
            return Optional.of(JSON.writeValueAsString(root));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }
}
