package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.TemporalProperty;
import java.util.List;
import java.util.Optional;
import org.apalache_mc.tla.jir.TlaExpressions;

/**
 * Module-level temporal property: the property formula and its fairness conditions.
 *
 * <p>Everything here is drawn over the caller's scope, which holds the state variables and the
 * auxiliary definitions. The formula is drawn in {@link LevelContext#TEMPORAL}, where
 * {@code [][A]_v}, {@code <><<A>>_v} and fairness are ordinary forms; the actions of the fairness
 * conditions are drawn in {@link LevelContext#ACTION}.
 */
final class PropertyGenFactory extends AbstractExprGenFactory {
    /** The bytes of the index that selects the edge of a fairness condition. Part of the byte encoding. */
    static final int EDGE_INDEX_BYTES = 2;

    PropertyGenFactory(GenerationContext context, IrTypeGenFactory types, IrExprGenFactory expressions) {
        super(context, types, expressions);
    }

    /**
     * Decodes one Boolean marker and, when it is odd, the formula and a terminated list of fairness
     * conditions each preceded by a Boolean choosing strong fairness. The formula is drawn first,
     * so a short section still decodes a formula rather than only fairness. Each part gets a node
     * budget of its own, so that a large formula does not reduce every condition to terminals.
     */
    Generator<Optional<TemporalProperty>> property(int expressionDepth) {
        return draw -> {
            if (!draw.drawBoolean()) {
                return Optional.empty();
            }
            var formula = draw.draw(formula(expressionDepth));
            var fairness = draw.draw(BasicGenerators.listOf(
                    context.withFreshNodeBudget(conditionDraw -> conditionDraw.draw(
                            fairness(conditionDraw.drawBoolean(), expressionDepth))),
                    0, context.config().modules().maximumFairnessConditions()));
            return Optional.of(new TemporalProperty(fairness, formula));
        };
    }

    /**
     * Decodes the property of a graph module (ADR 0027 §8): a Boolean marker, a terminated list of
     * fairness conditions, and a formula as {@link #property(int)} draws it. Each condition is a
     * Boolean marker for weak or strong fairness and a two-byte index that selects one of {@code
     * edges}, the disjuncts of the module's next-state action; it constrains a copy of that edge,
     * with {@code subscript}. The conditions come first because they are short: the formula takes
     * what remains.
     */
    Generator<Optional<TemporalProperty>> edgeProperty(List<TlaEx> edges, TlaEx subscript, int expressionDepth) {
        Generator<TlaEx> condition = draw -> {
            var strong = draw.drawBoolean();
            var edge = TlaExpressions.deepCopy(edges.get(draw.drawIndex(edges.size(), EDGE_INDEX_BYTES)));
            var variables = TlaExpressions.deepCopy(subscript);
            return strong ? builder().strongFair(variables, edge) : builder().weakFair(variables, edge);
        };
        return draw -> {
            if (!draw.drawBoolean()) {
                return Optional.empty();
            }
            var fairness = draw.draw(BasicGenerators.listOf(
                    condition, 0, context.config().modules().maximumFairnessConditions()));
            return Optional.of(new TemporalProperty(fairness, draw.draw(formula(expressionDepth))));
        };
    }

    /** Decodes a property formula under a node budget of its own. */
    Generator<TlaEx> formula(int expressionDepth) {
        return context.withFreshNodeBudget(atLevel(LevelContext.TEMPORAL, PrimitiveType.BOOL, expressionDepth));
    }
}
