package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Generator;

/** Constructs Boolean-valued expression generators. */
final class BooleanExprGenFactory extends AbstractExprGenFactory {
    BooleanExprGenFactory(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory) {
        super(context, typeFactory, expressionFactory);
    }

    /** Returns a generator for the selected Boolean form. */
    Generator<TlaEx> mkGen(BooleanExpressionKind kind, int remainingDepth) {
        return draw -> {
            var nextDepth = remainingDepth - 1;
            return switch (kind) {
                case BOOLEAN_LITERAL -> builder().bool(draw.drawBoolean());
                case EQUAL -> draw.draw(equal(remainingDepth, false));
                case NOT_EQUAL -> draw.draw(equal(remainingDepth, true));
                // These connectives pass a temporal context through to their operands. <=> does not:
                // TLC cannot handle an equivalence between temporal formulas,
                // https://github.com/tlaplus/tlaplus/issues/1029.
                case NOT -> builder().not(
                        draw.draw(sameLevel(PrimitiveType.BOOL, nextDepth)));
                case AND -> builder().and(
                        draw.draw(operands(sameLevel(PrimitiveType.BOOL, nextDepth))));
                case OR -> builder().or(
                        draw.draw(operands(sameLevel(PrimitiveType.BOOL, nextDepth))));
                case IMPLIES -> draw.draw(binary(
                        sameLevel(PrimitiveType.BOOL, nextDepth), builder()::implies));
                case EQUIVALENT -> draw.draw(binary(PrimitiveType.BOOL, nextDepth, builder()::equiv));
                case FORALL_BOUNDED ->
                    draw.draw(quantifier(true, true, remainingDepth));
                case EXISTS_BOUNDED ->
                    draw.draw(quantifier(false, true, remainingDepth));
                case FORALL_UNBOUNDED ->
                    draw.draw(quantifier(true, false, remainingDepth));
                case EXISTS_UNBOUNDED ->
                    draw.draw(quantifier(false, false, remainingDepth));
                case LESS_THAN -> draw.draw(binary(PrimitiveType.INT, nextDepth, builder()::lt));
                case GREATER_THAN -> draw.draw(binary(PrimitiveType.INT, nextDepth, builder()::gt));
                case LESS_EQUAL -> draw.draw(binary(PrimitiveType.INT, nextDepth, builder()::le));
                case GREATER_EQUAL -> draw.draw(binary(PrimitiveType.INT, nextDepth, builder()::ge));
                case IN -> draw.draw(membership(remainingDepth, false));
                case NOT_IN -> draw.draw(membership(remainingDepth, true));
                case SUBSET_EQUAL -> {
                    var elementType = draw.draw(typeFactory.valueType());
                    yield draw.draw(binary(new SetType(elementType), nextDepth, builder()::subsetEq));
                }
                case IS_FINITE_SET -> {
                    var elementType = draw.draw(typeFactory.valueType());
                    yield builder().isFiniteSet(
                            draw.draw(expression(new SetType(elementType), nextDepth)));
                }
                case PRIME_EQUAL -> {
                    var variable = draw.draw(context.chooseStateVariable());
                    var valueType = variable.type();
                    var name = builder().name(variable.name(), valueType.toTlaType());
                    yield builder().primeEq(
                            name, draw.draw(expression(valueType, nextDepth)));
                }
                case STUTTER -> draw.draw(subscripted(remainingDepth, builder()::stutter));
                case NO_STUTTER -> draw.draw(subscripted(remainingDepth, builder()::noStutter));
                case ENABLED -> builder().enabled(
                        draw.draw(atLevel(LevelContext.ACTION, PrimitiveType.BOOL, nextDepth)));
                case UNCHANGED -> builder().unchanged(draw.draw(
                        atLevel(LevelContext.STATE, draw.draw(typeFactory.valueType()), nextDepth)));
                case ACTION_THEN -> draw.draw(binary(
                        atLevel(LevelContext.ACTION, PrimitiveType.BOOL, nextDepth), builder()::actionThen));
                case ALWAYS -> builder().always(draw.draw(actionFreeTemporal(nextDepth)));
                case EVENTUALLY -> builder().eventually(draw.draw(actionFreeTemporal(nextDepth)));
                case LEADS_TO -> draw.draw(binary(actionFreeTemporal(nextDepth), builder()::leadsTo));
                case GUARANTEES -> draw.draw(binary(actionFreeTemporal(nextDepth), builder()::guarantees));
                case WEAK_FAIR -> draw.draw(fairness(false, remainingDepth));
                case STRONG_FAIR -> draw.draw(fairness(true, remainingDepth));
                case TEMPORAL_EXISTS ->
                    draw.draw(temporalQuantifier(true, remainingDepth));
                case TEMPORAL_FORALL ->
                    draw.draw(temporalQuantifier(false, remainingDepth));
            };
        };
    }

    /** Returns a generator for the selected temporal formula built around an action. */
    Generator<TlaEx> mkGen(TemporalActionExpressionKind kind, int remainingDepth) {
        return switch (kind) {
            case INFINITELY_OFTEN_ACTION -> subscripted(remainingDepth, (action, subscript) ->
                    builder().always(builder().eventually(builder().noStutter(action, subscript))));
            case EVENTUALLY_ALWAYS_ACTION -> subscripted(remainingDepth, (action, subscript) ->
                    builder().eventually(builder().always(builder().stutter(action, subscript))));
        };
    }

    /**
     * Returns an operand of {@code []}, {@code <>}, {@code ~>} or {@code -+->}, which may be temporal
     * but contain no action. TLC checks an action only in {@code []<>A} and {@code <>[]A}, and only
     * when a Boolean connective, not another temporal operator, surrounds it: it rejects
     * {@code <>[]<>A}, {@code []<>[]A}, {@code []~WF_v(A)} and {@code <>SF_v(A)}.
     */
    private Generator<TlaEx> actionFreeTemporal(int depth) {
        return atLevel(LevelContext.ACTION_FREE_TEMPORAL, PrimitiveType.BOOL, depth);
    }

    /** Returns a generator of equality or inequality over a generated value type. */
    private Generator<TlaEx> equal(int remainingDepth, boolean negate) {
        return draw -> {
            var type = draw.draw(typeFactory.valueType());
            var left = draw.draw(expression(type, remainingDepth - 1));
            var right = draw.draw(expression(type, remainingDepth - 1));
            return negate ? builder().neql(left, right) : builder().eql(left, right);
        };
    }

    /** Returns a quantifier generator with an arbitrary scoped predicate. */
    private Generator<TlaEx> quantifier(
            boolean universal, boolean bounded, int remainingDepth) {
        return typeFactory.valueType().flatMap(type -> bounded
                ? bounded("q", type, PrimitiveType.BOOL, remainingDepth - 1,
                        universal ? builder()::forall : builder()::exists)
                : unbounded("q", type, PrimitiveType.BOOL, remainingDepth - 1,
                        universal ? builder()::forall : builder()::exists));
    }

    /** Returns a generator of membership or non-membership. */
    private Generator<TlaEx> membership(int remainingDepth, boolean negate) {
        return draw -> {
            var type = draw.draw(typeFactory.valueType());
            var element = draw.draw(expression(type, remainingDepth - 1));
            var set = draw.draw(expression(new SetType(type), remainingDepth - 1));
            return negate ? builder().notIn(element, set) : builder().in(element, set);
        };
    }

    /** Returns a temporal quantifier generator with an arbitrary scoped predicate. */
    private Generator<TlaEx> temporalQuantifier(
            boolean existential, int remainingDepth) {
        return typeFactory.valueType().flatMap(type -> unbounded(
                "temporal", type, PrimitiveType.BOOL, remainingDepth - 1,
                existential ? builder()::temporalExists : builder()::temporalForAll));
    }

}
