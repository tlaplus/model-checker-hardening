package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.ConstT1;
import at.forsyte.apalache.tla.lir.VariantT1;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Generator;
import java.math.BigInteger;
import java.util.List;
import org.apalache_mc.tla.jir.ExpressionPair;
import org.apalache_mc.tla.jir.NamedExpression;
import org.apalache_mc.tla.jir.TlaBuilderExpr;

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
    Generator<TlaBuilderExpr> mkGen(
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
                case BOUNDED_CHOOSE -> draw.draw(boundedChoose(type, remainingDepth));
                case UNBOUNDED_CHOOSE -> draw.draw(unboundedChoose(type, remainingDepth));
                case CASE -> draw.draw(caseExpression(type, remainingDepth));
                case OPERATOR_APPLICATION ->
                    draw.draw(operatorApplication(type, remainingDepth));
                case LET -> draw.draw(letExpression(type, remainingDepth));
                case PRIME -> builder().prime(draw.draw(expression(type, nextDepth)));
                case FUNCTION_APPLICATION ->
                    draw.draw(functionApplication(type, remainingDepth));
                case FOLD_SET -> draw.draw(foldSet(type, remainingDepth));
                case FOLD_SEQUENCE -> draw.draw(foldSequence(type, remainingDepth));
                case HEAD -> builder().head(
                        draw.draw(expression(new SequenceType(type), nextDepth)));
                case VARIANT_GET_OR_ELSE ->
                    draw.draw(variantGetOrElse(type, remainingDepth));
                case VARIANT_GET_UNSAFE ->
                    draw.draw(variantGetUnsafe(type, remainingDepth));
            };
        };
    }

    /** Returns a byte-free generator of a closed expression for the requested type. */
    Generator<TlaBuilderExpr> terminal(IrType type) {
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
            case TupleType tupleType -> builder().tuple(BuilderArrays.expressions(
                    tupleType.elements().stream()
                            .map(element -> draw.draw(terminal(element)))
                            .toList()));
            case RecordType recordType -> builder().record(BuilderArrays.named(
                    recordType.fields().stream()
                            .map(field -> new NamedExpression<>(
                                    field.name(), draw.draw(terminal(field.type()))))
                            .toList()));
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

    /** Returns a generator of a bounded CHOOSE expression. */
    private Generator<TlaBuilderExpr> boundedChoose(IrType type, int remainingDepth) {
        return draw -> {
            var binding = context.freshBinding("bound", type);
            var bound = builder().name(binding.name(), type.toTlaType());
            var set = draw.draw(expression(new SetType(type), remainingDepth - 1));
            var predicate = draw.draw(context.withBinding(
                    binding, expression(PrimitiveType.BOOL, remainingDepth - 1)));
            return builder().choose(bound, set, predicate);
        };
    }

    /** Returns a generator of an unbounded CHOOSE expression. */
    private Generator<TlaBuilderExpr> unboundedChoose(IrType type, int remainingDepth) {
        return draw -> {
            var binding = context.freshBinding("chosen", type);
            var bound = builder().name(binding.name(), type.toTlaType());
            var predicate = draw.draw(context.withBinding(
                    binding, expression(PrimitiveType.BOOL, remainingDepth - 1)));
            return builder().choose(bound, predicate);
        };
    }

    /** Returns a generator of a CASE expression with a terminated branch collection. */
    private Generator<TlaBuilderExpr> caseExpression(IrType type, int remainingDepth) {
        return draw -> {
            var branches = draw.draw(BasicGenerators.listOf(
                    branchDraw -> new ExpressionPair<>(
                            branchDraw.draw(expression(
                                    PrimitiveType.BOOL, remainingDepth - 1)),
                            branchDraw.draw(expression(type, remainingDepth - 1))),
                    1,
                    context.config().maximumCollectionSize()));
            if (draw.drawBoolean()) {
                return builder().caseOther(
                        draw.draw(expression(type, remainingDepth - 1)),
                        BuilderArrays.pairs(branches));
            }
            return builder().caseSplit(BuilderArrays.pairs(branches));
        };
    }

    /** Returns a generator of an application with a generated operator signature. */
    private Generator<TlaBuilderExpr> operatorApplication(
            IrType resultType, int remainingDepth) {
        return draw -> {
            var binding = draw.draw(context.chooseOperatorReturning(resultType));
            var operatorType = (OperatorType) binding.type();
            var arguments = operatorType.arguments().stream()
                    .map(type -> draw.draw(expression(type, remainingDepth - 1)))
                    .toArray(TlaBuilderExpr[]::new);
            var operator = builder().name(binding.name(), operatorType.toTlaType());
            return builder().operApply(operator, arguments);
        };
    }

    /** Returns a generator of a LET whose body sees its local nullary operator. */
    private Generator<TlaBuilderExpr> letExpression(
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
    private Generator<TlaBuilderExpr> functionApplication(
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

    /** Returns a generator of a set fold. */
    private Generator<TlaBuilderExpr> foldSet(IrType resultType, int remainingDepth) {
        return draw -> {
            var elementType = draw.draw(typeFactory.valueType());
            var operatorType = new OperatorType(
                    List.of(resultType, elementType), resultType);
            return builder().foldSet(
                    draw.draw(otherFactory.lambda(operatorType, remainingDepth - 1)),
                    draw.draw(expression(resultType, remainingDepth - 1)),
                    draw.draw(expression(new SetType(elementType), remainingDepth - 1)));
        };
    }

    /** Returns a generator of a sequence fold. */
    private Generator<TlaBuilderExpr> foldSequence(
            IrType resultType, int remainingDepth) {
        return draw -> {
            var elementType = draw.draw(typeFactory.valueType());
            var operatorType = new OperatorType(
                    List.of(resultType, elementType), resultType);
            return builder().foldSeq(
                    draw.draw(otherFactory.lambda(operatorType, remainingDepth - 1)),
                    draw.draw(expression(resultType, remainingDepth - 1)),
                    draw.draw(expression(
                            new SequenceType(elementType), remainingDepth - 1)));
        };
    }

    /** Returns a generator of a variant access with a fallback value. */
    private Generator<TlaBuilderExpr> variantGetOrElse(
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
    private Generator<TlaBuilderExpr> variantGetUnsafe(
            IrType resultType, int remainingDepth) {
        return draw -> {
            var type = typeFactory.singleVariant(resultType);
            var tag = type.fields().getFirst().name();
            return builder().variantGetUnsafe(
                    tag, draw.draw(expression(type, remainingDepth - 1)));
        };
    }

    /** Returns a generator of an exactly typed scoped name. */
    private Generator<TlaBuilderExpr> name(IrType type) {
        return draw -> {
            var binding = draw.draw(context.chooseBinding(type));
            return builder().name(binding.name(), type.toTlaType());
        };
    }
}
