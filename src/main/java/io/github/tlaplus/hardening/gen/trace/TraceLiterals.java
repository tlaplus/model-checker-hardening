package io.github.tlaplus.hardening.gen.trace;

import at.forsyte.apalache.tla.lir.ConstT1;
import at.forsyte.apalache.tla.lir.FunT1;
import at.forsyte.apalache.tla.lir.RecRowT1;
import at.forsyte.apalache.tla.lir.SeqT1;
import at.forsyte.apalache.tla.lir.SetT1;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaType1;
import at.forsyte.apalache.tla.lir.TupT1;
import at.forsyte.apalache.tla.lir.VariantT1;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.ir.BuilderArrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apalache_mc.tla.jir.ExpressionPair;
import org.apalache_mc.tla.jir.NamedExpression;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;

/**
 * Builds the typed IR literal of a recorded value (ADR 0034 §3). The type of the value's variable
 * decides how its CBOR item is read (ADR 0034 §2):
 *
 * <ul>
 *   <li>a set, a sequence and a tuple are arrays of their elements;
 *   <li>a record is the array of its field values, in the order of its type's fields;
 *   <li>a function is an array of {@code [key, value]} pairs, which becomes {@code [x \in {k1, ...,
 *       kn} |-> CASE x = k1 -> v1 [] ... [] OTHER -> vn]};
 *   <li>a variant is {@code [tag, value]}, and a model value its printed name {@code name_OF_TYPE}.
 * </ul>
 *
 * <p>One instance builds the literals of one module: it numbers the bound variables of function
 * literals, so no two of them share a name.
 */
@ArchRole(Role.CODEC)
@Key
public final class TraceLiterals {
    /** Prefix of the bound variable of a function literal; the decoders draw no such name. */
    static final String BOUND_PREFIX = "traceKey";
    private static final String MODEL_SEPARATOR = "_OF_";

    private final TlaTypedScopeUncheckedBuilder builder = new TlaTypedScopeUncheckedBuilder();
    private int bindings;

    /**
     * Returns the literal of {@code value} at {@code type}.
     *
     * @throws InputRejectedException if the value does not have that type
     */
    public TlaEx literal(JsonNode value, TlaType1 type) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(type, "type");
        if (type == TlaTypes.BOOL) {
            require(value.isBoolean(), value, type);
            return builder.bool(value.booleanValue());
        }
        if (type == TlaTypes.INT) {
            require(value.isIntegralNumber(), value, type);
            return builder.integer(value.bigIntegerValue());
        }
        if (type == TlaTypes.STRING) {
            require(value.isTextual(), value, type);
            return builder.str(value.textValue());
        }
        return switch (type) {
            case ConstT1 constant -> {
                require(value.isTextual() && value.textValue().endsWith(MODEL_SEPARATOR + constant.name())
                        && value.textValue().length() > MODEL_SEPARATOR.length() + constant.name().length(), value, type);
                yield builder.constParsed(value.textValue());
            }
            case SetT1 set -> {
                var elements = elements(value, set.elem(), type);
                yield elements.isEmpty() ? builder.emptySet(set.elem()) : builder.enumSet(BuilderArrays.expressions(elements));
            }
            case SeqT1 sequence -> {
                var elements = elements(value, sequence.elem(), type);
                yield elements.isEmpty() ? builder.emptySeq(sequence.elem()) : builder.seq(BuilderArrays.expressions(elements));
            }
            case TupT1 tuple -> {
                var types = TlaTypes.tupleElements(tuple);
                require(value.isArray() && value.size() == types.size(), value, type);
                var elements = new ArrayList<TlaEx>();
                for (var index = 0; index < types.size(); index++) {
                    elements.add(literal(value.get(index), types.get(index)));
                }
                yield builder.tuple(BuilderArrays.expressions(elements));
            }
            case RecRowT1 record -> {
                var fields = TlaTypes.rowFields(record.row());
                require(TlaTypes.rowTail(record.row()).isEmpty() && value.isArray() && value.size() == fields.size(),
                        value, type);
                var named = new ArrayList<NamedExpression<TlaEx>>();
                var index = 0;
                for (var field : fields.entrySet()) {
                    named.add(new NamedExpression<>(field.getKey(), literal(value.get(index++), field.getValue())));
                }
                yield builder.record(BuilderArrays.named(named));
            }
            case FunT1 function -> function(value, function);
            case VariantT1 variant -> {
                var options = TlaTypes.rowFields(variant.row());
                require(TlaTypes.rowTail(variant.row()).isEmpty() && value.isArray() && value.size() == 2
                        && value.get(0).isTextual() && options.containsKey(value.get(0).textValue()), value, type);
                var tag = value.get(0).textValue();
                yield builder.variant(tag, literal(value.get(1), options.get(tag)), variant);
            }
            default -> throw new InputRejectedException("a trace holds no value of type " + type);
        };
    }

    /**
     * Returns {@code [x \in {k1, ..., kn} |-> CASE x = k1 -> v1 [] ... [] OTHER -> vn]}, or {@code [x
     * \in {} |-> t]} for an empty domain, where t is a fixed value of the result type.
     */
    private TlaEx function(JsonNode value, FunT1 type) {
        require(value.isArray(), value, type);
        var name = BOUND_PREFIX + bindings++;
        var bound = builder.name(name, type.arg());
        if (value.isEmpty()) {
            return builder.funDef(fixed(type.res()), BuilderArrays.pairs(
                    List.of(new ExpressionPair<>(bound, builder.emptySet(type.arg())))));
        }
        var keys = new ArrayList<TlaEx>();
        var arms = new ArrayList<ExpressionPair<TlaEx>>();
        TlaEx other = null;
        for (var index = 0; index < value.size(); index++) {
            var pair = value.get(index);
            require(pair.isArray() && pair.size() == 2, value, type);
            keys.add(literal(pair.get(0), type.arg()));
            var result = literal(pair.get(1), type.res());
            if (index < value.size() - 1) {
                // A fresh copy of the key: Apalache requires unique node identities.
                arms.add(new ExpressionPair<>(
                        builder.eql(builder.name(name, type.arg()), literal(pair.get(0), type.arg())), result));
            } else {
                other = result;
            }
        }
        var body = arms.isEmpty() ? other : builder.caseOther(other, BuilderArrays.pairs(arms));
        return builder.funDef(body, BuilderArrays.pairs(
                List.of(new ExpressionPair<>(bound, builder.enumSet(BuilderArrays.expressions(keys))))));
    }

    /** Returns a fixed closed value of a value type, the result of a function with no arguments. */
    private TlaEx fixed(TlaType1 type) {
        if (type == TlaTypes.BOOL) {
            return builder.bool(false);
        }
        if (type == TlaTypes.INT) {
            return builder.integer(0);
        }
        if (type == TlaTypes.STRING) {
            return builder.str("");
        }
        return switch (type) {
            case ConstT1 constant -> builder.constParsed("default" + MODEL_SEPARATOR + constant.name());
            case SetT1 set -> builder.emptySet(set.elem());
            case SeqT1 sequence -> builder.emptySeq(sequence.elem());
            case TupT1 tuple -> builder.tuple(BuilderArrays.expressions(
                    TlaTypes.tupleElements(tuple).stream().map(this::fixed).toList()));
            case RecRowT1 record -> builder.record(BuilderArrays.named(TlaTypes.rowFields(record.row()).entrySet()
                    .stream().map(field -> new NamedExpression<>(field.getKey(), fixed(field.getValue()))).toList()));
            case FunT1 function -> builder.funDef(fixed(function.res()), BuilderArrays.pairs(List.of(
                    new ExpressionPair<>(builder.name(BOUND_PREFIX + bindings++, function.arg()),
                            builder.emptySet(function.arg())))));
            case VariantT1 variant -> {
                var first = TlaTypes.rowFields(variant.row()).entrySet().iterator().next();
                yield builder.variant(first.getKey(), fixed(first.getValue()), variant);
            }
            default -> throw new InputRejectedException("a trace holds no value of type " + type);
        };
    }

    private List<TlaEx> elements(JsonNode value, TlaType1 element, TlaType1 type) {
        require(value.isArray(), value, type);
        var elements = new ArrayList<TlaEx>();
        value.forEach(item -> elements.add(literal(item, element)));
        return elements;
    }

    private static void require(boolean condition, JsonNode value, TlaType1 type) {
        if (!condition) {
            throw new InputRejectedException("a recorded value " + value + " does not have type " + type);
        }
    }
}
