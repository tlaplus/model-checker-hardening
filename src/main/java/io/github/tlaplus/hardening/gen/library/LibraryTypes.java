package io.github.tlaplus.hardening.gen.library;

import at.forsyte.apalache.tla.lir.*;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaTypes;

/** Structural inspection of imported type terms, including row variables. */
public final class LibraryTypes {
    private LibraryTypes() {}

    /** Children in deterministic structural order (record and variant fields are sorted by name). */
    public static List<TlaType1> children(TlaType1 type) {
        return switch (type) {
            case SetT1 ignored -> TlaTypes.children(type);
            case SeqT1 ignored -> TlaTypes.children(type);
            case FunT1 ignored -> TlaTypes.children(type);
            case TupT1 ignored -> TlaTypes.children(type);
            case OperT1 ignored -> TlaTypes.children(type);
            case RecRowT1 ignored -> TlaTypes.children(type);
            case VariantT1 ignored -> TlaTypes.children(type);
            case RowT1 ignored -> TlaTypes.children(type);
            case VarT1 ignored -> List.of();
            case ConstT1 ignored -> List.of();
            default -> {
                if (type != TlaTypes.BOOL && type != TlaTypes.INT && type != TlaTypes.STRING) {
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
}
