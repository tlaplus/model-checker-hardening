package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaType1;
import java.util.List;
import org.apalache_mc.tla.jir.NamedType;
import org.apalache_mc.tla.jir.TlaTypes;

/** Internal type model used to direct checked expression construction. */
sealed interface IrType
        permits PrimitiveType,
                ConstantType,
                SetType,
                SequenceType,
                FunctionType,
                TupleType,
                RecordType,
                VariantType,
                OperatorType {
    TlaType1 toTlaType();

    String prefix();
}

/** Primitive expression result types. */
enum PrimitiveType implements IrType {
    BOOL(TlaTypes.BOOL, "bool"),
    INT(TlaTypes.INT, "int"),
    STRING(TlaTypes.STRING, "str");

    private final TlaType1 type;
    private final String prefix;

    PrimitiveType(TlaType1 type, String prefix) {
        this.type = type;
        this.prefix = prefix;
    }

    @Override
    public TlaType1 toTlaType() {
        return type;
    }

    @Override
    public String prefix() {
        return prefix;
    }
}

/** Model-value type. */
record ConstantType(String name) implements IrType {
    @Override
    public TlaType1 toTlaType() {
        return TlaTypes.constant(name);
    }

    @Override
    public String prefix() {
        return "constant";
    }
}

/** Set type. */
record SetType(IrType element) implements IrType {
    @Override
    public TlaType1 toTlaType() {
        return TlaTypes.set(element.toTlaType());
    }

    @Override
    public String prefix() {
        return "set";
    }
}

/** Sequence type. */
record SequenceType(IrType element) implements IrType {
    @Override
    public TlaType1 toTlaType() {
        return TlaTypes.sequence(element.toTlaType());
    }

    @Override
    public String prefix() {
        return "seq";
    }
}

/** Function type. */
record FunctionType(IrType argument, IrType result) implements IrType {
    @Override
    public TlaType1 toTlaType() {
        return TlaTypes.function(argument.toTlaType(), result.toTlaType());
    }

    @Override
    public String prefix() {
        return "fun";
    }
}

/** Tuple type. */
record TupleType(List<IrType> elements) implements IrType {
    TupleType {
        elements = List.copyOf(elements);
    }

    @Override
    public TlaType1 toTlaType() {
        return TlaTypes.tuple(elements.stream()
                .map(IrType::toTlaType)
                .toArray(TlaType1[]::new));
    }

    @Override
    public String prefix() {
        return "tuple";
    }
}

/** Named component of a record or variant type. */
record Field(String name, IrType type) {}

/** Record type. */
record RecordType(List<Field> fields) implements IrType {
    RecordType {
        fields = List.copyOf(fields);
    }

    @Override
    public TlaType1 toTlaType() {
        return TlaTypes.rowRecord(fields.stream()
                .map(field -> new NamedType(field.name(), field.type().toTlaType()))
                .toArray(NamedType[]::new));
    }

    @Override
    public String prefix() {
        return "record";
    }
}

/** Variant type. */
record VariantType(List<Field> fields) implements IrType {
    VariantType {
        fields = List.copyOf(fields);
    }

    @Override
    public TlaType1 toTlaType() {
        return TlaTypes.variant(fields.stream()
                .map(field -> new NamedType(field.name(), field.type().toTlaType()))
                .toArray(NamedType[]::new));
    }

    @Override
    public String prefix() {
        return "variant";
    }
}

/** Operator type. */
record OperatorType(List<IrType> arguments, IrType result) implements IrType {
    OperatorType {
        arguments = List.copyOf(arguments);
    }

    @Override
    public TlaType1 toTlaType() {
        return TlaTypes.operator(
                result.toTlaType(),
                arguments.stream().map(IrType::toTlaType).toArray(TlaType1[]::new));
    }

    @Override
    public String prefix() {
        return "operator";
    }
}
