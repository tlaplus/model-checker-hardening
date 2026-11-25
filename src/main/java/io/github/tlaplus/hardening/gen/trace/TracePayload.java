package io.github.tlaplus.hardening.gen.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The payload of a {@code trace} input (ADR 0034 §2), a CBOR array in deterministic encoding:
 *
 * <pre>[base kind, base payload, [state, ...], ending]</pre>
 *
 * where each state is an array of values, one per variable, and the ending is {@code
 * ["invariant"]}, {@code ["loop", l]} or {@code ["prefix"]}. The base payload is the parent's input
 * bytes, so the entry decodes without its parent.
 *
 * <p>The layout is read without the base's types: decoding the values is left to {@link
 * TraceLiterals}, which knows them. Equal payloads encode to equal bytes, so the same trace from two
 * checkers is one entry.
 */
@ArchRole(Role.CODEC)
@Key
public record TracePayload(InputKind baseKind, byte[] base, Trace trace) {
    /** The CBOR codec of payloads and of single values, whose bytes order set elements. */
    static final ObjectMapper CBOR = new ObjectMapper(new CBORFactory())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final String INVARIANT = "invariant";
    private static final String LOOP = "loop";
    private static final String PREFIX = "prefix";
    /** The four items of a payload, in this order. */
    private static final int ITEMS = 4;

    public TracePayload {
        Objects.requireNonNull(baseKind, "baseKind");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(trace, "trace");
        if (baseKind.derived()) {
            throw new IllegalArgumentException("a trace replays a generated input, not a " + baseKind.encodedName());
        }
        base = base.clone();
    }

    @Override
    public byte[] base() {
        return base.clone();
    }

    /** Encodes this payload as the input bytes of a {@code trace} entry. */
    public byte[] encode() {
        var root = NODES.arrayNode(ITEMS);
        root.add(baseKind.encodedName());
        root.add(NODES.binaryNode(base));
        root.add(states(trace));
        root.add(ending(trace.ending()));
        return bytes(root);
    }

    /**
     * Encodes a trace alone as {@code [states, ending]}, the form a checker stage stores under {@code
     * stages.<checker>.trace} (ADR 0034 §5).
     */
    public static byte[] encodeTrace(Trace trace) {
        Objects.requireNonNull(trace, "trace");
        var root = NODES.arrayNode(2);
        root.add(states(trace));
        root.add(ending(trace.ending()));
        return bytes(root);
    }

    /**
     * Decodes a trace that {@link #encodeTrace} encoded.
     *
     * @throws InputRejectedException if the bytes are not one well-formed trace
     */
    public static Trace decodeTrace(byte[] encoded) {
        var root = tree(encoded);
        if (root == null || !root.isArray() || root.size() != 2) {
            throw new InputRejectedException("a stored trace is an array of states and an ending");
        }
        try {
            return new Trace(states(root.get(0)), ending(root.get(1)));
        } catch (IllegalArgumentException exception) {
            throw new InputRejectedException("malformed stored trace: " + exception.getMessage(), exception);
        }
    }

    /**
     * Decodes the layout of a {@code trace} payload.
     *
     * @throws InputRejectedException if the bytes are not one well-formed payload
     */
    public static TracePayload decode(byte[] input) {
        var root = tree(input);
        if (root == null || !root.isArray() || root.size() != ITEMS) {
            throw new InputRejectedException("a trace payload is an array of " + ITEMS + " items");
        }
        var kindName = root.get(0);
        var kind = kindName.isTextual() ? InputKind.fromEncodedName(kindName.textValue()) : Optional.<InputKind>empty();
        if (kind.isEmpty() || kind.get().derived()) {
            throw new InputRejectedException("a trace payload names a generated base kind, not " + kindName);
        }
        if (!root.get(1).isBinary()) {
            throw new InputRejectedException("a trace payload holds its base as a byte string");
        }
        try {
            return new TracePayload(kind.get(), root.get(1).binaryValue(),
                    new Trace(states(root.get(2)), ending(root.get(3))));
        } catch (IOException | IllegalArgumentException exception) {
            throw new InputRejectedException("malformed trace payload: " + exception.getMessage(), exception);
        }
    }

    /**
     * Returns the canonical encoding of one value, by whose bytes a set orders its elements and a
     * function its pairs (ADR 0034 §2).
     */
    static byte[] bytes(JsonNode value) {
        try {
            return CBOR.writeValueAsBytes(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot encode a CBOR tree", exception);
        }
    }

    private static JsonNode tree(byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded");
        try {
            return CBOR.readTree(encoded);
        } catch (IOException exception) {
            throw new InputRejectedException("a trace is not CBOR: " + exception.getMessage(), exception);
        }
    }

    private static ArrayNode states(Trace trace) {
        var states = NODES.arrayNode(trace.states().size());
        for (var state : trace.states()) {
            var values = states.addArray();
            state.forEach(values::add);
        }
        return states;
    }

    private static List<List<JsonNode>> states(JsonNode states) {
        if (!states.isArray()) {
            throw new InputRejectedException("a trace payload holds its states as an array");
        }
        var result = new ArrayList<List<JsonNode>>();
        for (var state : states) {
            if (!state.isArray()) {
                throw new InputRejectedException("a state is an array of values");
            }
            var values = new ArrayList<JsonNode>();
            state.forEach(values::add);
            result.add(values);
        }
        return result;
    }

    private static ArrayNode ending(TraceEnding ending) {
        var node = NODES.arrayNode();
        switch (ending) {
            case TraceEnding.Invariant() -> node.add(INVARIANT);
            case TraceEnding.Loop(var index) -> node.add(LOOP).add(index);
            case TraceEnding.Prefix() -> node.add(PREFIX);
        }
        return node;
    }

    private static TraceEnding ending(JsonNode node) {
        if (!node.isArray() || node.isEmpty() || !node.get(0).isTextual()) {
            throw new InputRejectedException("a trace ending is an array that starts with its name");
        }
        var name = node.get(0).textValue();
        if (name.equals(INVARIANT) && node.size() == 1) {
            return new TraceEnding.Invariant();
        }
        if (name.equals(PREFIX) && node.size() == 1) {
            return new TraceEnding.Prefix();
        }
        if (name.equals(LOOP) && node.size() == 2 && node.get(1).canConvertToInt() && node.get(1).isIntegralNumber()) {
            return new TraceEnding.Loop(node.get(1).intValue());
        }
        throw new InputRejectedException("unknown trace ending " + node);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TracePayload payload && baseKind == payload.baseKind
                && Arrays.equals(base, payload.base) && trace.equals(payload.trace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseKind, Arrays.hashCode(base), trace);
    }

    @Override
    public String toString() {
        return "TracePayload[" + baseKind.encodedName() + ", " + base.length + " bytes, " + trace + "]";
    }
}
