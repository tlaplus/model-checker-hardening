package io.github.tlaplus.hardening.workflow.tlc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import tlc2.tool.TLCState;
import tlc2.value.IValue;
import tlc2.value.impl.BoolValue;
import tlc2.value.impl.FcnRcdValue;
import tlc2.value.impl.IntValue;
import tlc2.value.impl.ModelValue;
import tlc2.value.impl.RecordValue;
import tlc2.value.impl.SetEnumValue;
import tlc2.value.impl.StringValue;
import tlc2.value.impl.TupleValue;
import tlc2.value.impl.Value;
import util.UniqueString;

/**
 * Writes TLC's values in the Informal Trace Format (ADR 0034 §5), as a recorded trace carries them
 * from the worker to its stage.
 *
 * <p>TLC does not record types: a tuple and a sequence are both tuples, and a function on {@code
 * 1..n} may be either a tuple or a function. The stage reads each value at its variable's type, which
 * settles the form, so this class writes what TLC holds: a tuple as an array, a record as an object,
 * a function as {@code #map}, and any other enumerable set as {@code #set}.
 */
final class TlcItfValues {
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private TlcItfValues() {}

    /** Returns the values of {@code state}, by variable name. */
    static ObjectNode state(TLCState state) {
        var result = NODES.objectNode();
        for (Map.Entry<UniqueString, IValue> entry : state.getVals().entrySet()) {
            result.set(entry.getKey().toString(), value((Value) entry.getValue()));
        }
        return result;
    }

    /**
     * Returns one value.
     *
     * @throws IllegalArgumentException for a value that is neither a scalar, a tuple, a record, a
     *     function nor an enumerable set
     */
    static JsonNode value(Value value) {
        return switch (value) {
            case BoolValue bool -> NODES.booleanNode(bool.val);
            case IntValue integer -> NODES.numberNode(integer.val);
            case StringValue string -> NODES.textNode(string.val.toString());
            case ModelValue model -> NODES.textNode(model.val.toString());
            case TupleValue tuple -> {
                var array = NODES.arrayNode();
                for (var element : tuple.elems) {
                    array.add(value(element));
                }
                yield array;
            }
            case RecordValue record -> {
                var object = NODES.objectNode();
                for (var index = 0; index < record.names.length; index++) {
                    object.set(record.names[index].toString(), value(record.values[index]));
                }
                yield object;
            }
            case FcnRcdValue function -> map(function);
            default -> {
                var function = value.toFcnRcd();
                if (function instanceof FcnRcdValue converted) {
                    yield map(converted);
                }
                var set = value.toSetEnum();
                if (set instanceof SetEnumValue enumerated) {
                    var elements = NODES.arrayNode();
                    for (var index = 0; index < enumerated.elems.size(); index++) {
                        elements.add(value(enumerated.elems.elementAt(index)));
                    }
                    yield NODES.objectNode().set("#set", elements);
                }
                throw new IllegalArgumentException("a trace cannot record the value " + value.getKindString());
            }
        };
    }

    private static JsonNode map(FcnRcdValue function) {
        var domain = function.getDomainAsValues();
        var pairs = NODES.arrayNode();
        for (var index = 0; index < domain.length; index++) {
            pairs.add(NODES.arrayNode().add(value(domain[index])).add(value(function.values[index])));
        }
        return NODES.objectNode().set("#map", pairs);
    }
}
