package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.ConstT1;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.VariantT1;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Generator;
import io.vavr.Function3;
import java.math.BigInteger;
import java.util.List;
import java.util.function.Function;
import org.apalache_mc.tla.jir.ExpressionPair;

/** Constructs terminal and type-polymorphic expression generators. */
final class GeneralExprGenFactory extends AbstractExprGenFactory {
    private final OtherExprGenFactory otherFactory;

    GeneralExprGenFactory(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory,
            OtherExprGenFactory otherFactory) {
        super(context, typeFactory, expressionFactory);
        this.otherFactory = otherFactory;
    }

    /** Returns a generator for the selected general form. */
    Generator<TlaEx> mkGen(
            GeneralExpressionKind kind, IrType type, int remainingDepth) {
        return draw -> {
            var nextDepth = remainingDepth - 1;
            return switch (kind) {
                case TERMINAL -> draw.draw(terminal(type));
                case NAME -> draw.draw(name(type));
                case IF_THEN_ELSE -> builder().ite(
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)),
                        draw.draw(expression(type, nextDepth)),
                        draw.draw(expression(type, nextDepth)));
                case LABEL -> builder().label(
                        draw.draw(expression(type, nextDepth)), context.fresh("label"));
                // CHOOSE predicates see their bound name, but the domain does not.
                case BOUNDED_CHOOSE -> draw.draw(bounded(
                        "bound", type, PrimitiveType.BOOL, nextDepth, builder()::choose));
                case UNBOUNDED_CHOOSE -> draw.draw(unbounded(
                        "chosen", type, PrimitiveType.BOOL, nextDepth, builder()::choose));
                case CASE -> draw.draw(caseExpression(type, remainingDepth));
                case OPERATOR_APPLICATION ->
                    draw.draw(operatorApplication(type, remainingDepth));
                case LET -> draw.draw(letExpression(type, remainingDepth));
                case PRIME -> builder().prime(draw.draw(expression(type, nextDepth)));
                case FUNCTION_APPLICATION ->
                    draw.draw(functionApplication(type, remainingDepth));
                case FOLD_SET -> draw.draw(fold(type, remainingDepth, SetType::new, builder()::foldSet));
                case FOLD_SEQUENCE -> draw.draw(fold(type, remainingDepth, SequenceType::new, builder()::foldSeq));
                case HEAD -> builder().head(
                        draw.draw(expression(new SequenceType(type), nextDepth)));
                case VARIANT_GET_OR_ELSE ->
                    draw.draw(variantGetOrElse(type, remainingDepth));
                case VARIANT_GET_UNSAFE ->
                    draw.draw(variantGetUnsafe(type, remainingDepth));
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

    /** Returns the closed terminal for a type, ignoring the current lexical scope. */
    private Generator<TlaEx> closedTerminal(IrType type) {
        return draw -> switch (type) {
            case PrimitiveType primitive -> switch (primitive) {
                case BOOL -> builder().bool(false);
                case INT -> builder().integer(BigInteger.ZERO);
                case STRING -> builder().str("");
            };
            case ConstantType constantType ->
                builder().constant("default", (ConstT1) constantType.toTlaType());
            case SetType(IrType element) -> builder().emptySet(element.toTlaType());
            case SequenceType(IrType element) -> builder().emptySeq(element.toTlaType());
            case FunctionType functionType -> {
                var binding = context.freshBinding("terminalArg", functionType.argument());
                var variable = builder().name(
                        binding.name(), functionType.argument().toTlaType());
                var domain = builder().emptySet(functionType.argument().toTlaType());
                var pair = new ExpressionPair<>(variable, domain);
                yield builder().funDef(
                        draw.draw(terminal(functionType.result())),
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

    /** Returns a generator of a CASE expression with a terminated branch collection. */
    private Generator<TlaEx> caseExpression(IrType type, int remainingDepth) {
        return draw -> {
            var branches = draw.draw(BasicGenerators.listOf(
                    branchDraw -> new ExpressionPair<>(
                            branchDraw.draw(expression(
                                    PrimitiveType.BOOL, remainingDepth - 1)),
                            branchDraw.draw(expression(type, remainingDepth - 1))),
                    1,
                    context.config().expressions().maximumCollectionSize()));
            if (draw.drawBoolean()) {
                return builder().caseOther(
                        draw.draw(expression(type, remainingDepth - 1)),
                        BuilderArrays.pairs(branches));
            }
            return builder().caseSplit(BuilderArrays.pairs(branches));
        };
    }

    /** Returns a generator of an application with a generated operator signature. */
    private Generator<TlaEx> operatorApplication(
            IrType resultType, int remainingDepth) {
        return draw -> {
            var binding = draw.draw(context.chooseOperatorReturning(resultType));
            var operatorType = (OperatorType) binding.type();
            var arguments = operatorType.arguments().stream()
                    .map(type -> draw.draw(expression(type, remainingDepth - 1)))
                    .toArray(TlaEx[]::new);
            var operator = builder().name(binding.name(), operatorType.toTlaType());
            return builder().operApply(operator, arguments);
        };
    }

    /** Returns a generator of a LET whose body sees its local nullary operator. */
    private Generator<TlaEx> letExpression(
            IrType resultType, int remainingDepth) {
        return draw -> {
            var operatorType = new OperatorType(List.of(), resultType);
            var binding = context.freshBinding("LocalOp", operatorType);
            var declaration = builder().decl(
                    binding.name(),
                    draw.draw(expression(resultType, remainingDepth - 1)));
            var body = draw.draw(context.withBinding(
                    binding, expression(resultType, remainingDepth - 1)));
            return builder().letIn(body, declaration);
        };
    }

    /** Returns a generator of a function application with a generated argument type. */
    private Generator<TlaEx> functionApplication(
            IrType resultType, int remainingDepth) {
        return draw -> {
            var argumentType = draw.draw(typeFactory.valueType());
            return builder().funApply(
                    draw.draw(expression(
                            new FunctionType(argumentType, resultType),
                            remainingDepth - 1)),
                    draw.draw(expression(argumentType, remainingDepth - 1)));
        };
    }

    /** Draws a set or sequence fold's lambda, initial value and collection in that order. */
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

    /** Returns a generator of a variant access with a fallback value. */
    private Generator<TlaEx> variantGetOrElse(
            IrType resultType, int remainingDepth) {
        return draw -> {
            var type = typeFactory.singleVariant(resultType);
            var tag = type.fields().getFirst().name();
            return builder().variantGetOrElse(
                    tag,
                    draw.draw(expression(type, remainingDepth - 1)),
                    draw.draw(expression(resultType, remainingDepth - 1)));
        };
    }

    /** Returns a generator of an unchecked-at-runtime variant payload access. */
    private Generator<TlaEx> variantGetUnsafe(
            IrType resultType, int remainingDepth) {
        return draw -> {
            var type = typeFactory.singleVariant(resultType);
            var tag = type.fields().getFirst().name();
            return builder().variantGetUnsafe(
                    tag, draw.draw(expression(type, remainingDepth - 1)));
        };
    }

    /** Returns a generator of an exactly typed scoped name. */
    private Generator<TlaEx> name(IrType type) {
        return draw -> {
            var binding = draw.draw(context.chooseBinding(type));
            return builder().name(binding.name(), type.toTlaType());
        };
    }
}
