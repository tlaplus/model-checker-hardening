package io.github.tlaplus.hardening.gen.trace;

import at.forsyte.apalache.tla.lir.ConstT1;
import at.forsyte.apalache.tla.lir.FunT1;
import at.forsyte.apalache.tla.lir.RecRowT1;
import at.forsyte.apalache.tla.lir.SeqT1;
import at.forsyte.apalache.tla.lir.SetT1;
import at.forsyte.apalache.tla.lir.TlaType1;
import at.forsyte.apalache.tla.lir.TlaVarDecl;
import at.forsyte.apalache.tla.lir.TupT1;
import at.forsyte.apalache.tla.lir.VariantT1;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.UnaryOperator;
import org.apalache_mc.tla.jir.TlaTypes;

/**
 * Reads a counterexample that a checker worker recorded (ADR 0034 §5) into a canonical {@link
 * Trace}. A worker returns the JSON
 *
 * <pre>{"ending": "invariant" | "loop" | "prefix", "loop": l, "states": [{"x": v, ...}, ...]}</pre>
 *
 * whose values follow the Informal Trace Format: {@code {"#bigint": "n"}} or a number for an
 * integer, {@code {"#set": [...]}}, {@code {"#tup": [...]}}, {@code {"#map": [[k, v], ...]}}, an
 * array for a sequence, an object for a record, and {@code {"tag": t, "value": v}} for a variant.
 * A lasso already ends in a copy of its loop state.
 *
 * <p>The value's type, which the worker does not know, decides how it is read. It also settles the
 * forms TLC does not tell apart: a sequence or a function on {@code 1..n} may arrive as an array or
 * as a map, and a tuple as an array. A set is ordered by the encoded bytes of its elements and a
 * function by those of its keys, so equal values encode to equal bytes.
 */
@ArchRole(Role.CODEC)
@Key
public final class ItfTraces {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private ItfTraces() {}

    /**
     * Reads a recorded trace over {@code variables}, in their declaration order.
     *
     * @throws IllegalArgumentException if the trace is malformed, lacks a variable, or holds a value
     *     that does not have its variable's type
     */
    public static Trace read(String json, List<TlaVarDecl> variables) {
        Objects.requireNonNull(json, "json");
        Objects.requireNonNull(variables, "variables");
        final JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("a recorded trace is not JSON: " + exception.getMessage(), exception);
        }
        var states = root.path("states");
        require(states.isArray() && !states.isEmpty(), "a recorded trace has states", root);
        var result = new ArrayList<List<JsonNode>>();
        for (var state : states) {
            var values = new ArrayList<JsonNode>();
            for (var variable : variables) {
                var value = state.get(variable.name());
                require(value != null, "a state lacks " + variable.name(), state);
                values.add(value(value, TlaTypes.typeOf(variable)));
            }
            result.add(values);
        }
        return new Trace(result, ending(root));
    }

    private static TraceEnding ending(JsonNode root) {
        return switch (root.path("ending").asText()) {
            case "invariant" -> new TraceEnding.Invariant();
            case "prefix" -> new TraceEnding.Prefix();
            case "loop" -> {
                require(root.path("loop").canConvertToInt(), "a lasso names its loop state", root);
                yield new TraceEnding.Loop(root.path("loop").intValue());
            }
            default -> throw new IllegalArgumentException("unknown trace ending: " + root.path("ending"));
        };
    }

    /** Returns the canonical item of an ITF value at {@code type}. */
    static JsonNode value(JsonNode value, TlaType1 type) {
        if (type == TlaTypes.BOOL) {
            require(value.isBoolean(), "a Boolean", value);
            return value;
        }
        if (type == TlaTypes.INT) {
            return integer(value);
        }
        if (type == TlaTypes.STRING) {
            require(value.isTextual(), "a string", value);
            return value;
        }
        return switch (type) {
            case ConstT1 constant -> {
                require(value.isTextual(), "a model value of " + constant.name(), value);
                yield value;
            }
            case SetT1 set -> {
                var elements = tagged(value, "#set");
                require(elements != null && elements.isArray(), "a set", value);
                yield sorted(elements, element -> value(element, set.elem()));
            }
            case SeqT1 sequence -> {
                var elements = new ArrayList<JsonNode>();
                indexed(value).forEach(element -> elements.add(value(element, sequence.elem())));
                yield NODES.arrayNode().addAll(elements);
            }
            case TupT1 tuple -> {
                var elements = tagged(value, "#tup");
                var array = elements != null ? elements : value;
                var types = TlaTypes.tupleElements(tuple);
                require(array.isArray() && array.size() == types.size(), "a tuple of " + types.size(), value);
                var result = NODES.arrayNode();
                for (var index = 0; index < types.size(); index++) {
                    result.add(value(array.get(index), types.get(index)));
                }
                yield result;
            }
            case RecRowT1 record -> {
                var fields = TlaTypes.rowFields(record.row());
                require(value.isObject(), "a record", value);
                var result = NODES.arrayNode();
                for (var field : fields.entrySet()) {
                    var item = value.get(field.getKey());
                    require(item != null, "a record with field " + field.getKey(), value);
                    result.add(value(item, field.getValue()));
                }
                yield result;
            }
            case FunT1 function -> function(value, function);
            case VariantT1 variant -> variant(value, variant);
            default -> throw new IllegalArgumentException("a trace holds no value of type " + type);
        };
    }

    /**
     * Returns {@code [tag, value]} of a variant. Apalache writes {@code {"tag": t, "value": v}}; TLC's
     * {@code Variant(t, v)} is the function {@code t :> v}, a map or a record of one field.
     */
    private static JsonNode variant(JsonNode value, VariantT1 type) {
        var options = TlaTypes.rowFields(type.row());
        String tag;
        JsonNode payload;
        var map = tagged(value, "#map");
        if (map != null) {
            require(map.isArray() && map.size() == 1 && map.get(0).isArray() && map.get(0).size() == 2
                    && map.get(0).get(0).isTextual(), "a variant", value);
            tag = map.get(0).get(0).textValue();
            payload = map.get(0).get(1);
        } else if (value.isObject() && value.size() == 2 && value.path("tag").isTextual() && value.has("value")) {
            tag = value.get("tag").textValue();
            payload = value.get("value");
        } else {
            require(value.isObject() && value.size() == 1, "a variant", value);
            var field = value.properties().iterator().next();
            tag = field.getKey();
            payload = field.getValue();
        }
        require(options.containsKey(tag), "a variant of " + options.keySet(), value);
        return NODES.arrayNode().add(tag).add(value(payload, options.get(tag)));
    }

    /**
     * Returns the pairs of a function, ordered by the encoded bytes of their keys. TLC prints a
     * function on {@code 1..n} as an array and one on strings as a record.
     */
    private static JsonNode function(JsonNode value, FunT1 type) {
        var pairs = NODES.arrayNode();
        var map = tagged(value, "#map");
        if (map != null) {
            require(map.isArray(), "a function", value);
            map.forEach(pair -> {
                require(pair.isArray() && pair.size() == 2, "a function pair", pair);
                pairs.add(NODES.arrayNode().add(value(pair.get(0), type.arg())).add(value(pair.get(1), type.res())));
            });
        } else if (value.isArray()) {
            for (var index = 0; index < value.size(); index++) {
                pairs.add(NODES.arrayNode().add(value(NODES.numberNode(index + 1), type.arg()))
                        .add(value(value.get(index), type.res())));
            }
        } else {
            require(value.isObject() && type.arg() == TlaTypes.STRING, "a function", value);
            value.properties().forEach(field -> pairs.add(NODES.arrayNode()
                    .add(NODES.textNode(field.getKey())).add(value(field.getValue(), type.res()))));
        }
        var byKey = new TreeMap<byte[], JsonNode>(Arrays::compareUnsigned);
        pairs.forEach(pair -> require(byKey.put(TracePayload.bytes(pair.get(0)), pair) == null,
                "a function with distinct keys", value));
        return NODES.arrayNode().addAll(byKey.values());
    }

    /** Returns the elements of a sequence: an array, or a map whose keys are {@code 1..n}. */
    private static List<JsonNode> indexed(JsonNode value) {
        var map = tagged(value, "#map");
        if (map == null) {
            var array = tagged(value, "#tup");
            var elements = array != null ? array : value;
            require(elements.isArray(), "a sequence", value);
            var result = new ArrayList<JsonNode>();
            elements.forEach(result::add);
            return result;
        }
        require(map.isArray(), "a sequence", value);
        var byIndex = new TreeMap<BigInteger, JsonNode>();
        map.forEach(pair -> {
            require(pair.isArray() && pair.size() == 2, "a sequence pair", pair);
            byIndex.put(integer(pair.get(0)).bigIntegerValue(), pair.get(1));
        });
        var expected = BigInteger.ONE;
        for (var index : byIndex.keySet()) {
            require(index.equals(expected), "a sequence indexed from 1", value);
            expected = expected.add(BigInteger.ONE);
        }
        return new ArrayList<>(byIndex.values());
    }

    /** Returns the canonical item of an integer: a number, or a bignum beyond 64 bits. */
    static JsonNode integer(JsonNode value) {
        BigInteger integer;
        var big = tagged(value, "#bigint");
        if (big != null) {
            require(big.isTextual(), "an integer", value);
            try {
                integer = new BigInteger(big.textValue());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("not an integer: " + value, exception);
            }
        } else {
            require(value.isIntegralNumber(), "an integer", value);
            integer = value.bigIntegerValue();
        }
        // The node a CBOR reader produces for the encoded integer, so equal values are equal nodes.
        if (integer.bitLength() < Integer.SIZE) {
            return NODES.numberNode(integer.intValue());
        }
        return integer.bitLength() < Long.SIZE ? NODES.numberNode(integer.longValue()) : NODES.numberNode(integer);
    }

    /** Returns a set's distinct elements, ordered by their encoded bytes. */
    private static JsonNode sorted(JsonNode elements, UnaryOperator<JsonNode> canonical) {
        var byBytes = new TreeMap<byte[], JsonNode>(Arrays::compareUnsigned);
        elements.forEach(element -> {
            var item = canonical.apply(element);
            byBytes.put(TracePayload.bytes(item), item);
        });
        return NODES.arrayNode().addAll(byBytes.values());
    }

    /** Returns the payload of an ITF object {@code {"tag": payload}}, or {@code null} for another value. */
    private static JsonNode tagged(JsonNode value, String tag) {
        if (!value.isObject() || value.size() != 1) {
            return null;
        }
        Map.Entry<String, JsonNode> field = value.properties().iterator().next();
        return field.getKey().equals(tag) ? field.getValue() : null;
    }

    private static void require(boolean condition, String expected, JsonNode value) {
        if (!condition) {
            throw new IllegalArgumentException("expected " + expected + " in a recorded trace, found " + value);
        }
    }
}
