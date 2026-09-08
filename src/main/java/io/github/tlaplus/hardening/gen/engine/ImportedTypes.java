package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.*;
import java.util.List;
import static io.github.tlaplus.hardening.common.ScalaCollections.*;

/** Converts concrete imported type terms at the private generator boundary. */
final class ImportedTypes {
    private ImportedTypes() {}

    static IrType from(TlaType1 type) {
        if (type.equals(BoolT1$.MODULE$)) return PrimitiveType.BOOL;
        if (type.equals(IntT1$.MODULE$)) return PrimitiveType.INT;
        if (type.equals(StrT1$.MODULE$)) return PrimitiveType.STRING;
        return switch (type) {
            case ConstT1 constant -> new ConstantType(constant.name());
            case SetT1 set -> new SetType(from(set.elem()));
            case SeqT1 sequence -> new SequenceType(from(sequence.elem()));
            case FunT1 function -> new FunctionType(from(function.arg()), from(function.res()));
            case TupT1 tuple -> new TupleType(list(tuple.elems()).stream().map(ImportedTypes::from).toList());
            case RecRowT1 record -> new RecordType(fields(record.row()));
            case VariantT1 variant -> new VariantType(fields(variant.row()));
            case OperT1 operator -> new OperatorType(list(operator.args()).stream()
                    .map(ImportedTypes::from).toList(), from(operator.res()));
            default -> throw new IllegalArgumentException("expected a supported concrete type: " + type);
        };
    }

    private static List<Field> fields(RowT1 row) {
        if (row.other().isDefined()) throw new IllegalArgumentException("uninstantiated row: " + row);
        return map(row.fieldTypes()).entrySet().stream()
                .map(field -> new Field(field.getKey(), from(field.getValue()))).toList();
    }
}
