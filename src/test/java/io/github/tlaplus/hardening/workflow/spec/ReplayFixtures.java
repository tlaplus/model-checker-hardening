package io.github.tlaplus.hardening.workflow.spec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.trace.Trace;
import io.github.tlaplus.hardening.gen.trace.TraceEnding;
import io.github.tlaplus.hardening.gen.trace.TracePayload;
import java.util.ArrayList;
import java.util.List;

/**
 * Trace entries over the modules that an empty input decodes to, whose behaviors are known
 * without a checker:
 *
 * <ul>
 *   <li>the empty {@code module} input has {@code var0 : Bool} and {@code step}, with {@code Init
 *       == var0 = FALSE /\ step = 0}, one action {@code step < 5 /\ var0' = FALSE /\ step' = step +
 *       1}, and {@code Inv == var0}, which every state violates;
 *   <li>the empty {@code graph} input is a spine of 20 nodes over {@code var0 : Bool}, {@code pc}
 *       and {@code step}, whose node actions set {@code var0} to {@code FALSE}, with target 10 and
 *       step bound 10.
 * </ul>
 */
public final class ReplayFixtures {
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private ReplayFixtures() {}

    /** A trace entry of the empty module input over the {@code (var0, step)} states given. */
    public static CorpusInput module(TraceEnding ending, Object... states) {
        var values = new ArrayList<List<JsonNode>>();
        for (var index = 0; index < states.length; index += 2) {
            values.add(List.of(NODES.booleanNode((Boolean) states[index]), NODES.numberNode((Integer) states[index + 1])));
        }
        return entry(InputKind.MODULE, new Trace(values, ending));
    }

    /**
     * A trace entry of the empty graph input along its spine for {@code steps} transitions, with
     * {@code pc} set to {@code pcs[i]} in state i when {@code pcs} names it, and to i otherwise.
     */
    public static CorpusInput graph(TraceEnding ending, int steps, int... pcs) {
        var values = new ArrayList<List<JsonNode>>();
        for (var index = 0; index <= steps; index++) {
            var pc = index < pcs.length ? pcs[index] : index;
            values.add(List.of(NODES.booleanNode(false), NODES.numberNode(pc), NODES.numberNode(index)));
        }
        return entry(InputKind.GRAPH, new Trace(values, ending));
    }

    private static CorpusInput entry(InputKind base, Trace trace) {
        return new CorpusInput(InputKind.TRACE, new TracePayload(base, new byte[0], trace).encode());
    }
}
