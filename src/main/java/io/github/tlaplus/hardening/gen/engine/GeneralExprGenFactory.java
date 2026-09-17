package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.ConstT1;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.VariantT1;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Generator;
import io.vavr.Function3;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apalache_mc.tla.jir.ExpressionPair;

/**
 * Constructs terminal and type-polymorphic expression generators.
 */
final class GeneralExprGenFactory extends AbstractExprGenFactory {
    private final OtherExprGenFactory otherFactory;

    private final IndexedValues indexedValues;

    GeneralExprGenFactory(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory,
            OtherExprGenFactory otherFactory) {
        super(context, typeFactory, expressionFactory);
        this.otherFactory = otherFactory;
        this.indexedValues = new IndexedValues(context);
    }

    /**
     * Returns a generator for the selected general form.
     */
    Generator<TlaEx> mkGen(
            GeneralExpressionKind kind, IrType type, int remainingDepth) {
        return draw -> {
            var nextDepth = remainingDepth - 1;
            return switch (kind) {
                case TERMINAL -> draw.draw(terminal(type));
                case NAME -> draw.draw(name(type));
                case IF_THEN_ELSE -> builder().ite(
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)),
                        draw.draw(sameLevel(type, nextDepth)),
                        draw.draw(sameLevel(type, nextDepth)));
                // The parameters are read before the body is drawn: a binder introduced inside
                // the labeled expression is not in scope at the label and must not be declared.
                // A label is itself a definition, so its body starts a new label scope: a nested
                // label declares only the binders introduced between the two.
                case LABEL -> {
                    var parameters = labelArguments(context.fresh("label"));
                    yield builder().label(
                            draw.draw(context.withDefinitionBoundary(
                                    sameLevel(type, nextDepth))),
                            parameters);
                }
                // CHOOSE predicates see their bound name, but the domain does not.
                case BOUNDED_CHOOSE -> draw.draw(bounded(
                        "bound", type, PrimitiveType.BOOL, nextDepth, builder()::choose));
                case UNBOUNDED_CHOOSE -> draw.draw(unbounded(
                        "chosen", type, PrimitiveType.BOOL, nextDepth, builder()::choose));
                case CASE -> draw.draw(caseExpression(type, remainingDepth));
                case OPERATOR_APPLICATION -> draw.draw(operatorApplication(type, remainingDepth));
                case LET -> draw.draw(letExpression(type, remainingDepth));
                case PRIME -> builder().prime(draw.draw(atLevel(LevelContext.STATE, type, nextDepth)));
                case FUNCTION_APPLICATION -> draw.draw(functionApplication(type, remainingDepth));
                case FOLD_SET -> draw.draw(fold(type, remainingDepth, SetType::new, builder()::foldSet));
                case FOLD_SEQUENCE -> draw.draw(fold(type, remainingDepth, SequenceType::new, builder()::foldSeq));
                case HEAD -> builder().head(
                        draw.draw(expression(new SequenceType(type), nextDepth)));
                case VARIANT_GET_OR_ELSE -> draw.draw(variantGetOrElse(type, remainingDepth));
                case VARIANT_GET_UNSAFE -> draw.draw(variantGetUnsafe(type, remainingDepth));
            };
        };
    }

    /**
     * Returns a byte-free generator of a closed expression for the requested type.
     *
     * <p>Terminal construction is the most common leaf, so what it returns decides whether a
     * starved lambda body, quantifier body, or comprehension refers to the name it just
     * introduced or to a constant unrelated to it. When bindings of exactly this type are
     * visible, successive terminals rotate over them and then the closed terminal, so that two
     * sibling leaves differ rather than collapsing into a tautology. Operator types keep their
     * lambda terminal, because an operator name is not a value.
     */
    Generator<TlaEx> terminal(IrType type) {
        return draw -> {
            if (!(type instanceof OperatorType)) {
                var binding = context.nextTerminalBinding(type);
                if (binding.isPresent()) {
                    return builder().name(binding.get().name(), type.toTlaType());
                }
            }
            return draw.draw(closedTerminal(type));
        };
    }

    /**
     * Returns the byte-free terminal for a type that ignores the bindings of that type in scope.
     * Components of a composite type still rotate over bindings of their own types.
     */
    Generator<TlaEx> closedTerminal(IrType type) {
        return draw -> switch (type) {
            case PrimitiveType primitive -> switch (primitive) {
                case BOOL -> builder().bool(false);
                case INT -> builder().integer(BigInteger.valueOf(context.config().expressions().integers().base()));
                case STRING -> builder().str("");
            };
            case ConstantType constantType -> builder().constant("default", (ConstT1) constantType.toTlaType());
            case SetType(IrType element) -> {
                var size = terminalSize(element);
                yield size == 0 ? builder().emptySet(element.toTlaType()) : builder().enumSet(indexed(element, size));
            }
            case SequenceType(IrType element) -> {
                var size = terminalSize(element);
                yield size == 0 ? builder().emptySeq(element.toTlaType()) : builder().seq(indexed(element, size));
            }
            case FunctionType functionType -> {
                var argument = functionType.argument();
                var size = terminalSize(argument);
                var binding = context.freshBinding("terminalArg", argument);
                var variable = builder().name(binding.name(), argument.toTlaType());
                var domain = size == 0 ? builder().emptySet(argument.toTlaType()) : builder().enumSet(indexed(argument, size));
                var pair = new ExpressionPair<>(variable, domain);
                yield builder().funDef(
                        draw.draw(context.atoms().within(Math.max(1, size), terminal(functionType.result()))),
                        BuilderArrays.pairs(List.of(pair)));
            }
            case TupleType tupleType -> draw.draw(tuple(tupleType, this::terminal));
            case RecordType recordType -> draw.draw(record(recordType, this::terminal));
            case VariantType variantType -> {
                var field = variantType.fields().getFirst();
                yield builder().variant(
                        field.name(),
                        draw.draw(terminal(field.type())),
                        (VariantT1) variantType.toTlaType());
            }
            case OperatorType operatorType -> draw.draw(otherFactory.lambda(operatorType, 0));
        };
    }

    /**
     * Returns the size of a collection terminal over {@code element}: the configured base size,
     * bounded by the value-atom budget and by the distinct indexed values of the element type.
     */
    private int terminalSize(IrType element) {
        var base = context.config().expressions().collections().baseSize();
        return Math.min(Math.min(base, context.atoms().current()), IndexedValues.distinctValues(element));
    }

    /** Returns the first {@code size} indexed values of {@code element}. */
    private TlaEx[] indexed(IrType element, int size) {
        var values = new TlaEx[size];
        for (var k = 1; k <= size; k++) {
            values[k - 1] = indexedValues.value(element, k);
        }
        return values;
    }

    /**
     * Returns a generator of a CASE expression with a terminated branch collection.
     */
    private Generator<TlaEx> caseExpression(IrType type, int remainingDepth) {
        return draw -> {
            var branches = draw.draw(BasicGenerators.listOf(
                    branchDraw -> new ExpressionPair<>(
                            branchDraw.draw(expression(
                                    PrimitiveType.BOOL, remainingDepth - 1)),
                            branchDraw.draw(sameLevel(type, remainingDepth - 1))),
                    1,
                    context.config().expressions().collections().maximumSize()));
            if (draw.drawBoolean()) {
                return builder().caseOther(
                        draw.draw(sameLevel(type, remainingDepth - 1)),
                        BuilderArrays.pairs(branches));
            }
            return builder().caseSplit(BuilderArrays.pairs(branches));
        };
    }

    /**
     * Returns a generator of an application with a generated operator signature.
     */
    private Generator<TlaEx> operatorApplication(
            IrType resultType, int remainingDepth) {
        return draw -> {
            var binding = draw.draw(context.chooseOperatorReturning(resultType));
            var operatorType = (OperatorType) binding.type();
            var arguments = operatorType.arguments().stream()
                    .map(type -> draw.draw(atLevel(LevelContext.STATE, type, remainingDepth - 1)))
                    .toArray(TlaEx[]::new);
            var operator = builder().name(binding.name(), operatorType.toTlaType());
            return builder().operApply(operator, arguments);
        };
    }

    /**
     * Returns the {@code label} builder arguments: the label name followed by the formal
     * parameters TLA+ requires, which are exactly the binders whose scope contains the label.
     */
    private String[] labelArguments(String name) {
        return Stream.concat(Stream.of(name), context.labelParameters().stream())
                .toArray(String[]::new);
    }

    /**
     * Returns a generator of a LET with a terminated, non-empty list of local operators, whose body
     * sees all of them.
     *
     * <p>Each declaration draws its parameter types, which may include operator parameters, and
     * then one Boolean: even keeps the LET's own type as its result, odd draws a value type. Its
     * body sees its parameters and the declarations before it, but not itself, so no declaration
     * is recursive.
     *
     * <p>A declaration body is drawn at state level whatever the context, so that every local
     * operator is applicable wherever the LET body may apply it; the body itself passes the
     * context through.
     */
    private Generator<TlaEx> letExpression(
            IrType resultType, int remainingDepth) {
        return draw -> {
            var bindings = new ArrayList<ScopedName>();
            var declarations = draw.draw(BasicGenerators.listOf(
                    declarationDraw -> {
                        var parameterTypes = declarationDraw.draw(typeFactory.parameterTypes());
                        var declaredResult = declarationDraw.drawBoolean()
                                ? declarationDraw.draw(typeFactory.valueType())
                                : resultType;
                        var parameters = context.definitionParameters("parameter", parameterTypes);
                        var binding = context.freshDefinition(
                                "LocalOp", new OperatorType(parameterTypes, declaredResult));
                        var visible = new ArrayList<ScopedName>(bindings);
                        visible.addAll(parameters.bindings());
                        var body = declarationDraw.draw(context.withBindings(
                                visible,
                                context.withDefinitionBoundary(
                                        atLevel(LevelContext.STATE, declaredResult, remainingDepth - 1))));
                        bindings.add(binding);
                        return builder().decl(binding.name(), body, parameters.declarations());
                    },
                    1,
                    context.config().expressions().collections().maximumSize()));
            var body = draw.draw(context.withBindings(
                    bindings, sameLevel(resultType, remainingDepth - 1)));
            return builder().letIn(body, declarations.toArray(TlaOperDecl[]::new));
        };
    }

    /**
     * Returns a generator of a function application whose function type comes from scope or has a
     * drawn argument type.
     */
    private Generator<TlaEx> functionApplication(
            IrType resultType, int remainingDepth) {
        return draw -> {
            var type = (FunctionType) draw.draw(typeFactory.readType(
                    candidate -> candidate instanceof FunctionType function
                            && function.result().equals(resultType),
                    typeFactory.valueType().map(argument -> new FunctionType(argument, resultType))));
            return builder().funApply(
                    draw.draw(expression(type, remainingDepth - 1)),
                    draw.draw(expression(type.argument(), remainingDepth - 1)));
        };
    }

    /**
     * Draws a set or sequence fold's lambda, initial value and collection in that order.
     */
    private Generator<TlaEx> fold(
            IrType resultType, int remainingDepth, Function<IrType, IrType> collection,
            Function3<TlaEx, TlaEx, TlaEx, TlaEx> operation) {
        return draw -> {
            var elementType = draw.draw(typeFactory.valueType());
            var operatorType = new OperatorType(List.of(resultType, elementType), resultType);
            return operation.apply(
                    draw.draw(otherFactory.lambda(operatorType, remainingDepth - 1)),
                    draw.draw(expression(resultType, remainingDepth - 1)),
                    draw.draw(expression(collection.apply(elementType), remainingDepth - 1)));
        };
    }

    /**
     * Returns a generator of a variant access with a fallback value.
     */
    private Generator<TlaEx> variantGetOrElse(
            IrType resultType, int remainingDepth) {
        return draw -> {
            var read = draw.draw(typeFactory.variantCarrying(resultType));
            return builder().variantGetOrElse(
                    read.tag(),
                    draw.draw(expression(read.type(), remainingDepth - 1)),
                    draw.draw(expression(resultType, remainingDepth - 1)));
        };
    }

    /**
     * Returns a generator of an unchecked-at-runtime variant payload access.
     */
    private Generator<TlaEx> variantGetUnsafe(
            IrType resultType, int remainingDepth) {
        return draw -> {
            var read = draw.draw(typeFactory.variantCarrying(resultType));
            return builder().variantGetUnsafe(
                    read.tag(), draw.draw(expression(read.type(), remainingDepth - 1)));
        };
    }

    /**
     * Returns a generator of an exactly typed scoped name.
     */
    private Generator<TlaEx> name(IrType type) {
        return draw -> {
            var binding = draw.draw(context.chooseBinding(type));
            return builder().name(binding.name(), type.toTlaType());
        };
    }
}
