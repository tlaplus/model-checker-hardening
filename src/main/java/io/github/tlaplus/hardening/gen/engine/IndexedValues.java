package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.common.Preconditions;
import java.math.BigInteger;
import java.util.List;
import at.forsyte.apalache.tla.lir.ConstT1;
import org.apalache_mc.tla.jir.ExpressionPair;
import org.apalache_mc.tla.jir.NamedExpression;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import at.forsyte.apalache.tla.lir.VariantT1;

/**
 * The byte-free k-th value of a value type, from which collection terminals take distinct
 * elements (ADR 0011).
 *
 * <p>{@code value(T, k)} is injective in {@code k} unless every atom of {@code T} is Boolean, in
 * which case it has period two; {@link #distinctValues} reports that bound.
 */
final class IndexedValues {
    /** Upper bound reported for a type whose indexed values never repeat. */
    static final int UNBOUNDED = Integer.MAX_VALUE;

    private final GenerationContext context;

    IndexedValues(GenerationContext context) {
        this.context = context;
    }

    /** Returns the number of distinct values {@link #value} produces for {@code type}. */
    static int distinctValues(IrType type) {
        return onlyBooleanAtoms(type) ? 2 : UNBOUNDED;
    }

    /** Returns the closed k-th value of a value type, for {@code k >= 1}. */
    TlaEx value(IrType type, int k) {
        Preconditions.require(k >= 1, "k must be positive");
        return switch (type) {
            case PrimitiveType primitive -> switch (primitive) {
                case BOOL -> builder().bool(k % 2 == 1);
                case INT -> builder().integer(BigInteger.valueOf(k));
                case STRING -> builder().str(Integer.toString(k));
            };
            case ConstantType constant -> builder().constant("value" + k, (ConstT1) constant.toTlaType());
            case SetType(IrType element) -> builder().enumSet(value(element, k));
            case SequenceType(IrType element) -> builder().seq(value(element, k));
            case FunctionType(IrType argument, IrType result) -> {
                var binding = context.freshBinding("indexedArg", argument);
                var variable = builder().name(binding.name(), argument.toTlaType());
                var domain = builder().enumSet(value(argument, k));
                yield builder().funDef(value(result, k),
                        BuilderArrays.pairs(List.of(new ExpressionPair<>(variable, domain))));
            }
            case TupleType tuple -> builder().tuple(BuilderArrays.expressions(
                    tuple.elements().stream().map(element -> value(element, k)).toList()));
            case RecordType record -> builder().record(BuilderArrays.named(record.fields().stream()
                    .map(field -> new NamedExpression<>(field.name(), value(field.type(), k)))
                    .toList()));
            case VariantType variant -> {
                var field = variant.fields().getFirst();
                yield builder().variant(field.name(), value(field.type(), k), (VariantT1) variant.toTlaType());
            }
            case OperatorType operator -> throw new IllegalArgumentException("not a value type: " + operator);
        };
    }

    private static boolean onlyBooleanAtoms(IrType type) {
        return switch (type) {
            case PrimitiveType primitive -> primitive == PrimitiveType.BOOL;
            case VariantType variant -> onlyBooleanAtoms(variant.fields().getFirst().type());
            case OperatorType operator -> false;
            default -> type.components().stream().allMatch(IndexedValues::onlyBooleanAtoms);
        };
    }

    private TlaTypedScopeUncheckedBuilder builder() {
        return context.builder();
    }
}
