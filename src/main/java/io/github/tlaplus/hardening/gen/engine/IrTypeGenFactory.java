package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.VarT1;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.library.LibraryTypes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * Factory for deferred generators of internal types within the configured remaining-depth budget.
 *
 * <p>The returned generators capture this run's {@link GenerationContext}, but consume no bytes
 * until they are invoked with a draw.
 */
final class IrTypeGenFactory {
    private static final List<TypeKind> PRIMITIVE_TYPE_CANDIDATES = List.of(
            TypeKind.BOOL, TypeKind.INT, TypeKind.STRING, TypeKind.CONSTANT);
    private static final List<TypeKind> VALUE_TYPE_CANDIDATES = List.of(
            TypeKind.BOOL,
            TypeKind.INT,
            TypeKind.STRING,
            TypeKind.CONSTANT,
            TypeKind.SET,
            TypeKind.SEQUENCE,
            TypeKind.FUNCTION,
            TypeKind.TUPLE,
            TypeKind.RECORD,
            TypeKind.VARIANT);
    private static final List<TypeKind> ALL_TYPE_CANDIDATES = List.of(TypeKind.values());

    private final GenerationContext context;
    private final List<TypeKind> primitiveTypeKinds;
    private final List<TypeKind> valueTypeKinds;
    private final List<TypeKind> allTypeKinds;
    private final Map<Integer, List<TypeInstantiation>> templates = new HashMap<>();
    private final Map<IrType, Boolean> enabled = new HashMap<>();

    IrTypeGenFactory(GenerationContext context) {
        this.context = context;
        primitiveTypeKinds = enabledKinds(PRIMITIVE_TYPE_CANDIDATES);
        valueTypeKinds = enabledKinds(VALUE_TYPE_CANDIDATES);
        allTypeKinds = enabledKinds(ALL_TYPE_CANDIDATES);
    }

    /** Returns a generator of enabled type kinds, including enabled operator types. */
    Generator<IrType> anyType() {
        return mkGen(context.config().expressions().maximumTypeDepth(), OperatorTypes.ANY);
    }

    /** Returns a generator of enabled non-operator types for use as expression values. */
    Generator<IrType> valueType() {
        return mkGen(context.config().expressions().maximumTypeDepth(), OperatorTypes.NONE);
    }

    /** Draws a value type within the residual budget of a polymorphic type variable. */
    Generator<IrType> valueType(int remainingDepth) {
        return mkGen(remainingDepth, OperatorTypes.NONE);
    }

    /**
     * Returns a generator of a definition's parameter types: a terminated, possibly empty list in
     * which each type is a value type or, while the {@code operator} category is enabled, the type
     * of an operator parameter such as {@code F(_, _)}.
     *
     * <p>Every definition kind draws its signature here, so a {@code LET} declaration, an auxiliary
     * operator and an action operator may each be higher-order.
     */
    Generator<List<IrType>> parameterTypes() {
        return BasicGenerators.listOf(
                mkGen(context.config().expressions().maximumTypeDepth(), OperatorTypes.PARAMETER),
                0,
                context.config().expressions().maximumCollectionSize());
    }

    /**
     * Chooses the type of a value that a form reads: after one Boolean, spent whatever follows,
     * either a type reachable from the bindings in scope that {@code accepts} admits, or a fresh
     * one.
     *
     * <p>Record field names and variant tags are fresh for every drawn type, and a drawn function
     * type matches a visible one only by chance, so a fresh type is rarely the type of a visible
     * name, and the expression then requested for it is almost always a literal built on the spot.
     * A reachable type is one that a name, or a read of a name, can supply.
     */
    Generator<IrType> readType(Predicate<IrType> accepts, Generator<IrType> fresh) {
        return draw -> {
            var fromScope = draw.drawBoolean();
            var candidates = context.reachableTypes().stream()
                    .filter(accepts)
                    .filter(this::isEnabled)
                    .toList();
            return fromScope && !candidates.isEmpty()
                    ? draw.choose(candidates)
                    : draw.draw(fresh);
        };
    }

    /**
     * Draws up to {@code maximumOthers} further component types, then where the required component
     * sits among them, and builds a type from the components in order.
     */
    Generator<IrType> containing(
            IrType component, int maximumOthers, Function<List<IrType>, IrType> of) {
        return draw -> {
            var others = draw.draw(BasicGenerators.listOf(componentType(), 0, maximumOthers));
            var components = new ArrayList<IrType>(others);
            components.add((int) draw.drawLong(0, others.size()), component);
            return of.apply(List.copyOf(components));
        };
    }

    /** A component type one level below the configured type depth, like a drawn record's. */
    Generator<IrType> componentType() {
        return valueType(
                Math.max(0, context.config().expressions().maximumTypeDepth() - 1));
    }

    /** Chooses one of the positions at which {@code applied} holds a component of that type. */
    Generator<Integer> positionOf(IrType applied, IrType component) {
        return draw -> draw.choose(IntStream.range(0, applied.components().size())
                .filter(position -> applied.components().get(position).equals(component))
                .boxed()
                .toList());
    }

    /**
     * Chooses a variant type with a tag carrying {@code payload}, from scope or fresh, and then
     * which such tag to read.
     */
    Generator<TaggedVariant> variantCarrying(IrType payload) {
        return draw -> {
            var type = (VariantType) draw.draw(readType(
                    candidate -> candidate instanceof VariantType
                            && candidate.components().contains(payload),
                    freshVariant(payload)));
            int position = draw.draw(positionOf(type, payload));
            return new TaggedVariant(type, type.fields().get(position).name());
        };
    }

    /** Chooses any variant type, from scope or fresh. */
    Generator<VariantType> anyVariant() {
        return readType(VariantType.class::isInstance, componentType().flatMap(this::freshVariant))
                .map(VariantType.class::cast);
    }

    /** Draws a variant with fresh tags that carries {@code payload} among drawn alternatives. */
    private Generator<IrType> freshVariant(IrType payload) {
        return containing(
                payload,
                context.config().expressions().maximumCollectionSize() - 1,
                payloads -> new VariantType(payloads.stream()
                        .map(type -> new Field(context.freshTag(), type))
                        .toList()));
    }

    /**
     * Reports whether the type and every nested component use enabled syntax categories. The walk
     * is {@link LibraryTypes#categories}, which also checks imported signatures, so the two cannot
     * drift; exclusions are fixed for a run, so the answer is memoized rather than redrawn.
     */
    boolean isEnabled(IrType type) {
        return enabled.computeIfAbsent(type, requested -> Collections.disjoint(
                LibraryTypes.categories(requested.toTlaType()),
                context.config().ignoredCategories()));
    }

    /** Returns a recursive type recipe within the remaining nesting budget. */
    private Generator<IrType> mkGen(int remainingDepth, OperatorTypes operators) {
        return draw -> {
            var primitiveOnly = remainingDepth == 0;
            var kinds = primitiveOnly
                    ? primitiveTypeKinds
                    : (operators == OperatorTypes.NONE ? valueTypeKinds : allTypeKinds);
            var custom = templates(remainingDepth);
            int count = kinds.size() + custom.size();
            // A library can add enough templates to cross a byte-width boundary. Keep its
            // type choices fixed-width too, without changing the no-library encoding.
            int selected = context.config().library().exports().isEmpty()
                    ? (int) draw.drawLong(0, count - 1)
                    : draw.drawIndex(count, IrExprGenFactory.SELECTION_BYTES);
            if (selected >= kinds.size()) {
                return ImportedTypes.from(draw.draw(custom.get(selected - kinds.size()).generator(context, this)));
            }
            return switch (kinds.get(selected)) {
                case BOOL -> PrimitiveType.BOOL;
                case INT -> PrimitiveType.INT;
                case STRING -> PrimitiveType.STRING;
                case CONSTANT -> new ConstantType("MODEL");
                case SET -> draw.draw(
                        mkGen(remainingDepth - 1, OperatorTypes.NONE).map(SetType::new));
                case SEQUENCE -> draw.draw(
                        mkGen(remainingDepth - 1, OperatorTypes.NONE).map(SequenceType::new));
                case FUNCTION -> {
                    var component = mkGen(remainingDepth - 1, OperatorTypes.NONE);
                    yield draw.draw(component.flatMap(argument -> component.map(
                            result -> new FunctionType(argument, result))));
                }
                case TUPLE -> {
                    var element = mkGen(remainingDepth - 1, OperatorTypes.NONE);
                    yield draw.draw(BasicGenerators.listOf(
                                    element,
                                    1,
                                    context.config().expressions().maximumCollectionSize())
                            .map(TupleType::new));
                }
                case RECORD -> draw.draw(fields(remainingDepth - 1, context::freshField, RecordType::new));
                case VARIANT -> draw.draw(fields(remainingDepth - 1, context::freshTag, VariantType::new));
                case OPERATOR -> {
                    var component = mkGen(remainingDepth - 1, OperatorTypes.NONE);
                    yield draw.draw(BasicGenerators.listOf(
                                    component,
                                    operators.minimumArguments(),
                                    context.config().expressions().maximumCollectionSize())
                            .flatMap(arguments -> component.map(
                                    result -> new OperatorType(arguments, result))));
                }
            };
        };
    }

    /** Each field name precedes its type draw, even when that type exhausts the input. */
    private <T extends IrType> Generator<T> fields(
            int depth, Supplier<String> name, Function<List<Field>, T> constructor) {
        var component = mkGen(depth, OperatorTypes.NONE);
        Generator<Field> field = draw -> new Field(name.get(), draw.draw(component));
        return BasicGenerators.listOf(field, 1, context.config().expressions().maximumCollectionSize())
                .map(constructor);
    }

    /** Library result shapes make fixed field/tag/model names reachable without replacing standard types. */
    private List<TypeInstantiation> templates(int depth) {
        return templates.computeIfAbsent(depth, remaining -> context.config().library().exports().stream()
                .filter(export -> export.isEnabledWith(context.config().ignoredCategories()))
                .map(export -> TypeInstantiation.canonical(export.signature().res()))
                .filter(type -> !(type instanceof VarT1))
                .distinct()
                .map(type -> TypeInstantiation.plan(type, context.config(), remaining))
                .flatMap(Optional::stream).toList());
    }

    private List<TypeKind> enabledKinds(List<TypeKind> candidates) {
        return candidates.stream()
                .filter(kind -> kind.isAvailableWith(context.config().ignoredCategories()))
                .toList();
    }

    /** Which operator types a type request admits at its top level; components are always values. */
    private enum OperatorTypes {
        /** Value types only. */
        NONE(0),
        /** The expression root, which rejects an operator type but keeps it in its decoder. */
        ANY(0),
        /** A definition parameter: an operator parameter takes at least one argument. */
        PARAMETER(1);

        private final int minimumArguments;

        OperatorTypes(int minimumArguments) {
            this.minimumArguments = minimumArguments;
        }

        int minimumArguments() {
            return minimumArguments;
        }
    }

    /** Type choices in decoder order. */
    private enum TypeKind {
        BOOL(ExpressionCategory.CORE),
        INT(ExpressionCategory.CORE),
        STRING(ExpressionCategory.CORE),
        CONSTANT(ExpressionCategory.MODEL),
        SET(ExpressionCategory.SET),
        SEQUENCE(ExpressionCategory.SEQUENCE),
        FUNCTION(ExpressionCategory.FUNCTION, ExpressionCategory.SET),
        TUPLE(ExpressionCategory.TUPLE),
        RECORD(ExpressionCategory.RECORD),
        VARIANT(ExpressionCategory.VARIANT),
        OPERATOR(ExpressionCategory.OPERATOR);

        private final Set<ExpressionCategory> requiredCategories;

        TypeKind(ExpressionCategory category, ExpressionCategory... dependencies) {
            requiredCategories = ExpressionKind.requirements(category, dependencies);
        }

        boolean isAvailableWith(Set<ExpressionCategory> ignoredCategories) {
            for (var category : requiredCategories) {
                if (ignoredCategories.contains(category)) {
                    return false;
                }
            }
            return true;
        }
    }
}
