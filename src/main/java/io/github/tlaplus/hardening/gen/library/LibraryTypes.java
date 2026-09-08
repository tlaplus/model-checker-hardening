package io.github.tlaplus.hardening.gen.library;

import at.forsyte.apalache.tla.lir.*;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import static io.github.tlaplus.hardening.common.ScalaCollections.*;

/** Structural inspection of imported type terms, including row variables. */
public final class LibraryTypes {
    private LibraryTypes() {}

    public static TlaType1 type(TypeTag tag) {
        return TlaType1$.MODULE$.fromTypeTag(tag);
    }

    /** Children in deterministic structural order (record and variant fields are sorted by name). */
    public static List<TlaType1> children(TlaType1 type) {
        return switch (type) {
            case SetT1 set -> List.of(set.elem());
            case SeqT1 sequence -> List.of(sequence.elem());
            case FunT1 function -> List.of(function.arg(), function.res());
            case TupT1 tuple -> list(tuple.elems());
            case OperT1 operator -> {
                var types = new ArrayList<>(list(operator.args()));
                types.add(operator.res());
                yield List.copyOf(types);
            }
            case RecRowT1 record -> List.of(record.row());
            case VariantT1 variant -> List.of(variant.row());
            case RowT1 row -> {
                var types = new ArrayList<TlaType1>(map(row.fieldTypes()).values());
                if (row.other().isDefined()) types.add(row.other().get());
                yield List.copyOf(types);
            }
            case VarT1 ignored -> List.of();
            case ConstT1 ignored -> List.of();
            default -> {
                if (!type.equals(BoolT1$.MODULE$) && !type.equals(IntT1$.MODULE$)
                        && !type.equals(StrT1$.MODULE$)) {
                    throw new IllegalArgumentException("unsupported custom operator type: " + type);
                }
                yield List.of();
            }
        };
    }

    public static Set<ExpressionCategory> categories(TlaType1 type) {
        var result = EnumSet.noneOf(ExpressionCategory.class);
        switch (type) {
            case SetT1 ignored -> result.add(ExpressionCategory.SET);
            case SeqT1 ignored -> result.add(ExpressionCategory.SEQUENCE);
            case FunT1 ignored -> {
                result.add(ExpressionCategory.FUNCTION);
                result.add(ExpressionCategory.SET);
            }
            case TupT1 ignored -> result.add(ExpressionCategory.TUPLE);
            case RecRowT1 ignored -> result.add(ExpressionCategory.RECORD);
            case VariantT1 ignored -> result.add(ExpressionCategory.VARIANT);
            case OperT1 ignored -> result.add(ExpressionCategory.OPERATOR);
            case ConstT1 ignored -> result.add(ExpressionCategory.MODEL);
            default -> { }
        }
        children(type).forEach(child -> result.addAll(categories(child)));
        return Set.copyOf(result);
    }

    /** Row wrappers don't add a second level to record/variant nesting. */
    public static int depth(TlaType1 type) {
        var children = children(type);
        if (children.isEmpty()) return type instanceof RowT1 ? -1 : 0;
        int nested = children.stream().mapToInt(LibraryTypes::depth).max().orElse(0);
        return nested + (type instanceof RowT1 ? 0 : 1);
    }
}
