package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Generator;
import org.apalache_mc.tla.jir.TlaBuilderExpr;

/** Constructs Boolean-valued expression generators. */
final class BooleanExprGenFactory extends AbstractExprGenFactory {
    BooleanExprGenFactory(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory) {
        super(context, typeFactory, expressionFactory);
    }

    /** Returns a generator for the selected Boolean form. */
    Generator<TlaBuilderExpr> mkGen(BooleanExpressionKind kind, int remainingDepth) {
        return draw -> {
            var nextDepth = remainingDepth - 1;
            return switch (kind) {
                case BOOLEAN_LITERAL -> builder().bool(draw.drawBoolean());
                case EQUAL -> draw.draw(equal(remainingDepth, false));
                case NOT_EQUAL -> draw.draw(equal(remainingDepth, true));
                case NOT -> builder().not(
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)));
                case AND -> builder().and(BuilderArrays.expressions(draw.draw(
                        BasicGenerators.listOf(
                                expression(PrimitiveType.BOOL, nextDepth),
                                1,
                                context.config().maximumCollectionSize()))));
                case OR -> builder().or(BuilderArrays.expressions(draw.draw(
                        BasicGenerators.listOf(
                                expression(PrimitiveType.BOOL, nextDepth),
                                1,
                                context.config().maximumCollectionSize()))));
                case IMPLIES -> builder().implies(
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)),
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)));
                case EQUIVALENT -> builder().equiv(
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)),
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)));
                case FORALL_BOUNDED ->
                    draw.draw(quantifier(true, true, remainingDepth));
                case EXISTS_BOUNDED ->
                    draw.draw(quantifier(false, true, remainingDepth));
                case FORALL_UNBOUNDED ->
                    draw.draw(quantifier(true, false, remainingDepth));
                case EXISTS_UNBOUNDED ->
                    draw.draw(quantifier(false, false, remainingDepth));
                case LESS_THAN ->
                    draw.draw(integerComparison(remainingDepth, Comparison.LESS));
                case GREATER_THAN ->
                    draw.draw(integerComparison(remainingDepth, Comparison.GREATER));
                case LESS_EQUAL ->
                    draw.draw(integerComparison(remainingDepth, Comparison.LESS_EQUAL));
                case GREATER_EQUAL ->
                    draw.draw(integerComparison(remainingDepth, Comparison.GREATER_EQUAL));
                case IN -> draw.draw(membership(remainingDepth, false));
                case NOT_IN -> draw.draw(membership(remainingDepth, true));
                case SUBSET_EQUAL -> {
                    var elementType = draw.draw(typeFactory.valueType());
                    yield builder().subsetEq(
                            draw.draw(expression(new SetType(elementType), nextDepth)),
                            draw.draw(expression(new SetType(elementType), nextDepth)));
                }
                case IS_FINITE_SET -> {
                    var elementType = draw.draw(typeFactory.valueType());
                    yield builder().isFiniteSet(
                            draw.draw(expression(new SetType(elementType), nextDepth)));
                }
                case PRIME_EQUAL -> {
                    var valueType = draw.draw(typeFactory.valueType());
                    var name = builder().name(
                            context.fresh("state"), valueType.toTlaType());
                    yield builder().primeEq(
                            name, draw.draw(expression(valueType, nextDepth)));
                }
                case STUTTER -> builder().stutter(
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)),
                        draw.draw(expression(draw.draw(typeFactory.valueType()), nextDepth)));
                case NO_STUTTER -> builder().noStutter(
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)),
                        draw.draw(expression(draw.draw(typeFactory.valueType()), nextDepth)));
                case ENABLED -> builder().enabled(
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)));
                case UNCHANGED -> builder().unchanged(
                        draw.draw(expression(draw.draw(typeFactory.valueType()), nextDepth)));
                case ACTION_THEN -> builder().actionThen(
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)),
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)));
                case ALWAYS -> builder().always(
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)));
                case EVENTUALLY -> builder().eventually(
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)));
                case LEADS_TO -> builder().leadsTo(
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)),
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)));
                case GUARANTEES -> builder().guarantees(
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)),
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)));
                case WEAK_FAIR -> builder().weakFair(
                        draw.draw(expression(draw.draw(typeFactory.valueType()), nextDepth)),
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)));
                case STRONG_FAIR -> builder().strongFair(
                        draw.draw(expression(draw.draw(typeFactory.valueType()), nextDepth)),
                        draw.draw(expression(PrimitiveType.BOOL, nextDepth)));
                case TEMPORAL_EXISTS ->
                    draw.draw(temporalQuantifier(true, remainingDepth));
                case TEMPORAL_FORALL ->
                    draw.draw(temporalQuantifier(false, remainingDepth));
            };
        };
    }

    /** Returns a generator of equality or inequality over a generated value type. */
    private Generator<TlaBuilderExpr> equal(int remainingDepth, boolean negate) {
        return draw -> {
            var type = draw.draw(typeFactory.valueType());
            var left = draw.draw(expression(type, remainingDepth - 1));
            var right = draw.draw(expression(type, remainingDepth - 1));
            return negate ? builder().neql(left, right) : builder().eql(left, right);
        };
    }

    /** Returns a quantifier generator with an arbitrary scoped predicate. */
    private Generator<TlaBuilderExpr> quantifier(
            boolean universal, boolean bounded, int remainingDepth) {
        return draw -> {
            var type = draw.draw(typeFactory.valueType());
            var binding = context.freshBinding("q", type);
            var variable = builder().name(binding.name(), type.toTlaType());
            if (bounded) {
                var set = draw.draw(expression(new SetType(type), remainingDepth - 1));
                var predicate = draw.draw(context.withBinding(
                        binding, expression(PrimitiveType.BOOL, remainingDepth - 1)));
                return universal
                        ? builder().forall(variable, set, predicate)
                        : builder().exists(variable, set, predicate);
            }
            var predicate = draw.draw(context.withBinding(
                    binding, expression(PrimitiveType.BOOL, remainingDepth - 1)));
            return universal
                    ? builder().forall(variable, predicate)
                    : builder().exists(variable, predicate);
        };
    }

    /** Returns a generator of the selected integer comparison. */
    private Generator<TlaBuilderExpr> integerComparison(
            int remainingDepth, Comparison comparison) {
        return draw -> {
            var left = draw.draw(expression(PrimitiveType.INT, remainingDepth - 1));
            var right = draw.draw(expression(PrimitiveType.INT, remainingDepth - 1));
            return switch (comparison) {
                case LESS -> builder().lt(left, right);
                case GREATER -> builder().gt(left, right);
                case LESS_EQUAL -> builder().le(left, right);
                case GREATER_EQUAL -> builder().ge(left, right);
            };
        };
    }

    /** Returns a generator of membership or non-membership. */
    private Generator<TlaBuilderExpr> membership(int remainingDepth, boolean negate) {
        return draw -> {
            var type = draw.draw(typeFactory.valueType());
            var element = draw.draw(expression(type, remainingDepth - 1));
            var set = draw.draw(expression(new SetType(type), remainingDepth - 1));
            return negate ? builder().notIn(element, set) : builder().in(element, set);
        };
    }

    /** Returns a temporal quantifier generator with an arbitrary scoped predicate. */
    private Generator<TlaBuilderExpr> temporalQuantifier(
            boolean existential, int remainingDepth) {
        return draw -> {
            var type = draw.draw(typeFactory.valueType());
            var binding = context.freshBinding("temporal", type);
            var variable = builder().name(binding.name(), type.toTlaType());
            var predicate = draw.draw(context.withBinding(
                    binding, expression(PrimitiveType.BOOL, remainingDepth - 1)));
            return existential
                    ? builder().temporalExists(variable, predicate)
                    : builder().temporalForAll(variable, predicate);
        };
    }

    /** Integer comparison operations. */
    private enum Comparison {
        LESS,
        GREATER,
        LESS_EQUAL,
        GREATER_EQUAL
    }
}
