package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.TemporalProperty;
import java.util.Optional;

/**
 * Module-level temporal property: the property formula, its fairness conditions and its top-level
 * {@code [][A]_v} conjuncts.
 *
 * <p>Everything here is drawn over the caller's scope, which holds the state variables and the
 * auxiliary definitions. The formula is drawn in {@link LevelContext#TEMPORAL}; the actions of the
 * fairness conditions and the action constraints in {@link LevelContext#ACTION}. An action
 * constraint is generated here rather than as an expression form because both checkers accept
 * {@code [][A]_v} only as a top-level conjunct of a property.
 */
final class PropertyGenFactory extends AbstractExprGenFactory {
    PropertyGenFactory(GenerationContext context, IrTypeGenFactory types, IrExprGenFactory expressions) {
        super(context, types, expressions);
    }

    /**
     * Decodes one Boolean marker and, when it is odd, the formula, a terminated list of fairness
     * conditions each preceded by a Boolean choosing strong fairness, and a terminated list of
     * action constraints. The formula is drawn first, so a short section still decodes a formula
     * rather than only fairness. Each part gets a node budget of its own, so that a large formula
     * does not reduce every later condition to terminals.
     */
    Generator<Optional<TemporalProperty>> property(int expressionDepth) {
        return draw -> {
            if (!draw.drawBoolean()) {
                return Optional.empty();
            }
            var formula = draw.draw(context.withFreshNodeBudget(
                    atLevel(LevelContext.TEMPORAL, PrimitiveType.BOOL, expressionDepth)));
            var fairness = draw.draw(BasicGenerators.listOf(
                    context.withFreshNodeBudget(conditionDraw -> conditionDraw.draw(
                            fairness(conditionDraw.drawBoolean(), expressionDepth))),
                    0, context.config().modules().maximumFairnessConditions()));
            var constraints = draw.draw(BasicGenerators.listOf(
                    context.withFreshNodeBudget(subscripted(expressionDepth, builder()::stutter)
                            .map(action -> builder().always(action))),
                    0, context.config().expressions().maximumCollectionSize()));
            return Optional.of(new TemporalProperty(fairness, constraints, formula));
        };
    }
}
