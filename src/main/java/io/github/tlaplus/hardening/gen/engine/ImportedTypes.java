package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.*;
import java.util.List;
import org.apalache_mc.tla.jir.TlaTypes;

/** Converts concrete imported type terms at the private generator boundary. */
final class ImportedTypes {
    private ImportedTypes() {}

    static IrType from(TlaType1 type) {
        if (type == TlaTypes.BOOL) return PrimitiveType.BOOL;
        if (type == TlaTypes.INT) return PrimitiveType.INT;
        if (type == TlaTypes.STRING) return PrimitiveType.STRING;
        return switch (type) {
            case ConstT1 constant -> new ConstantType(constant.name());
            case SetT1 set -> new SetType(from(set.elem()));
            case SeqT1 sequence -> new SequenceType(from(sequence.elem()));
            case FunT1 function -> new FunctionType(from(function.arg()), from(function.res()));
            case TupT1 tuple -> new TupleType(TlaTypes.tupleElements(tuple).stream()
                    .map(ImportedTypes::from).toList());
            case RecRowT1 record -> new RecordType(fields(record.row()));
            case VariantT1 variant -> new VariantType(fields(variant.row()));
            case OperT1 operator -> new OperatorType(TlaTypes.operatorArguments(operator).stream()
                    .map(ImportedTypes::from).toList(), from(operator.res()));
            default -> throw new IllegalArgumentException("expected a supported concrete type: " + type);
        };
    }

    private static List<Field> fields(RowT1 row) {
        if (TlaTypes.rowTail(row).isPresent()) {
            throw new IllegalArgumentException("uninstantiated row: " + row);
        }
        return TlaTypes.rowFields(row).entrySet().stream()
                .map(field -> new Field(field.getKey(), from(field.getValue()))).toList();
    }
}
