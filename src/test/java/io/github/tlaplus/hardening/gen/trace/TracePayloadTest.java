package io.github.tlaplus.hardening.gen.trace;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import java.math.BigInteger;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class TracePayloadTest {
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    @Test
    void roundTripsEveryEnding() {
        for (var ending : List.of(new TraceEnding.Invariant(), new TraceEnding.Loop(1), new TraceEnding.Prefix())) {
            var payload = new TracePayload(InputKind.MODULE, new byte[] {1, 2, 3}, trace(ending));

            assertEquals(payload, TracePayload.decode(payload.encode()));
        }
    }

    /** The layout is the stored format of trace entries (ADR 0034 §2), so it is pinned byte for byte. */
    @Test
    void pinsTheEncoding() {
        var payload = new TracePayload(InputKind.GRAPH, new byte[] {7},
                new Trace(List.of(List.of(NODES.numberNode(1), NODES.booleanNode(true)),
                        List.of(NODES.numberNode(-2), NODES.booleanNode(false))), new TraceEnding.Loop(0)));

        // [ "graph", h'07', [[1, true], [-2, false]], ["loop", 0] ]
        assertEquals("84" + "656772617068" + "4107" + "82" + "8201f5" + "8221f4" + "82646c6f6f7000",
                HexFormat.of().formatHex(payload.encode()));
    }

    @Test
    void encodesWideIntegersAsBignums() {
        var wide = new BigInteger("123456789012345678901234567890");
        var payload = new TracePayload(InputKind.MODULE, new byte[0],
                new Trace(List.of(List.of(NODES.numberNode(wide))), new TraceEnding.Invariant()));

        assertEquals(wide, TracePayload.decode(payload.encode()).trace().states().getFirst().getFirst().bigIntegerValue());
    }

    @Test
    void equalTracesEncodeToEqualBytes() {
        var first = new TracePayload(InputKind.MODULE, new byte[] {5}, trace(new TraceEnding.Invariant()));
        var second = new TracePayload(InputKind.MODULE, new byte[] {5}, trace(new TraceEnding.Invariant()));

        assertArrayEquals(first.encode(), second.encode());
    }

    @Test
    void rejectsMalformedPayloads() {
        var valid = new TracePayload(InputKind.MODULE, new byte[] {1}, trace(new TraceEnding.Prefix())).encode();
        for (var hex : List.of(
                "",
                "ff",
                // [ "module" ]
                "81666d6f64756c65",
                // [ "trace", h'', [[1]], ["prefix"] ]: a trace does not replay a trace
                "84657472616365408181018166707265666978",
                // [ "module", h'', [[1], [1, 2]], ["prefix"] ]: unequal widths
                "84666d6f64756c65408281018201028166707265666978",
                // [ "module", h'', [[1]], ["loop", 0] ]: a loop needs a later state
                "84666d6f64756c6540818101" + "82646c6f6f7000",
                // [ "module", h'', [[1]], ["final"] ]
                "84666d6f64756c65408181018165" + "66696e616c")) {
            assertThrows(InputRejectedException.class, () -> TracePayload.decode(HexFormat.of().parseHex(hex)), hex);
        }
        var trailing = new byte[valid.length + 1];
        System.arraycopy(valid, 0, trailing, 0, valid.length);
        assertThrows(InputRejectedException.class, () -> TracePayload.decode(trailing));
    }

    /** A checker stage stores a trace alone, as [states, ending] (ADR 0034 §5). */
    @Test
    void roundTripsAStoredTrace() {
        var trace = trace(new TraceEnding.Loop(0));

        assertEquals(trace, TracePayload.decodeTrace(TracePayload.encodeTrace(trace)));
        assertThrows(InputRejectedException.class,
                () -> TracePayload.decodeTrace(new TracePayload(InputKind.MODULE, new byte[0], trace).encode()));
    }

    @Test
    void refusesADerivedBase() {
        assertThrows(IllegalArgumentException.class,
                () -> new TracePayload(InputKind.TRACE, new byte[0], trace(new TraceEnding.Prefix())));
    }

    private static Trace trace(TraceEnding ending) {
        List<List<JsonNode>> states = List.of(
                List.of(NODES.numberNode(0), NODES.arrayNode().add("a")),
                List.of(NODES.numberNode(1), NODES.arrayNode()),
                List.of(NODES.numberNode(2), NODES.arrayNode().add("a").add("b")));
        return new Trace(states, ending);
    }
}
