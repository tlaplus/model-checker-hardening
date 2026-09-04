package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import java.math.BigInteger;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.Generator;
import java.util.ArrayList;
import java.util.List;

/**
 * Constructs the initial-state predicate and the next-state action over declared state variables.
 *
 * <p>This is the only component that primes a name or builds an {@code UNCHANGED}. Both checkers
 * require a next state to be fully determined, so a disjunct must account for every declared
 * variable: what makes that checkable is that the account is assembled here, over the declaration
 * list, rather than emerging from an expression form that a negation or a quantifier could bury.
 * The expression factory this delegates to therefore has the action and temporal categories
 * excluded, and every value it produces reads the current state only.
 */
final class ActionGenFactory extends AbstractExprGenFactory {
    private final List<ScopedName> variables;
    private final ScopedName step;

    ActionGenFactory(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory,
            List<ScopedName> variables,
            ScopedName step) {
        super(context, typeFactory, expressionFactory);
        this.variables = List.copyOf(variables);
        this.step = step;
    }

    /**
     * Returns a generator of the initial-state predicate.
     *
     * <p>One conjunct per declared variable, in declaration order, so every variable is
     * constrained. The conjuncts are drawn without the state variables in scope: a conjunct that
     * read another variable would depend on an evaluation order the predicate does not fix.
     */
    Generator<TlaEx> initPredicate(int remainingDepth) {
        return draw -> {
            var conjuncts = new ArrayList<TlaEx>();
            for (var variable : variables) {
                var name = nameOf(variable);
                var type = variable.type();
                conjuncts.add(
                        nondeterministic(draw, type)
                                ? builder().in(
                                        name,
                                        draw.draw(expression(new SetType(type), remainingDepth)))
                                : builder().eql(
                                        name, draw.draw(expression(type, remainingDepth))));
            }
            conjuncts.add(builder().eql(nameOf(step), builder().integer(BigInteger.ZERO)));
            return builder().and(BuilderArrays.expressions(conjuncts));
        };
    }

    /**
     * Returns a generator of the next-state action: a disjunction of complete actions.
     *
     * <p>Each disjunct gets a node budget of its own, so that a large early disjunct does not
     * starve the later ones into terminals.
     */
    Generator<TlaEx> nextAction(int remainingDepth) {
        return BasicGenerators.listOf(
                        context.withFreshNodeBudget(action(remainingDepth)),
                        1,
                        context.config().maximumActions())
                .map(disjuncts -> builder().or(BuilderArrays.expressions(disjuncts)));
    }

    /**
     * Returns a generator of one complete action.
     *
     * <p>The bounded existentials are drawn first because their bound sets belong to the enclosing
     * scope, and their parameters are visible to everything inside. The guard and the assigned
     * values follow, and the variables left over are collected into one {@code UNCHANGED}.
     */
    private Generator<TlaEx> action(int remainingDepth) {
        return draw -> {
            var parameters = new ArrayList<Binding>();
            var bounds = new ArrayList<TlaEx>();
            // A parameter needs a set to range over, so a configuration that excludes sets
            // excludes parameters with them, and spends no bytes deciding that.
            var maximumParameters = context.config()
                            .ignoredCategories()
                            .contains(ExpressionCategory.SET)
                    ? 0
                    : context.config().maximumActionParameters();
            while (parameters.size() < maximumParameters && draw.drawBoolean()) {
                var type = draw.draw(typeFactory.valueType());
                // Creating the binder consumes no bytes, so it may precede its own bound set.
                var parameter = freshBinding("actionParam", type);
                parameters.add(parameter);
                bounds.add(draw.draw(expression(new SetType(type), remainingDepth - 1)));
            }
            var body = context.withBindings(
                    parameters.stream().map(Binding::name).toList(),
                    actionBody(remainingDepth));
            var action = draw.draw(body);
            for (var index = parameters.size() - 1; index >= 0; index--) {
                action = builder().exists(
                        parameters.get(index).variable(), bounds.get(index), action);
            }
            return action;
        };
    }

    /** Returns a generator of the guard, the assignments, and the completing UNCHANGED. */
    private Generator<TlaEx> actionBody(int remainingDepth) {
        return draw -> {
            var conjuncts = new ArrayList<TlaEx>();
            if (draw.drawBoolean()) {
                conjuncts.add(draw.draw(expression(PrimitiveType.BOOL, remainingDepth - 1)));
            }

            var assigned = new ArrayList<ScopedName>();
            var unchanged = new ArrayList<ScopedName>();
            for (var variable : variables) {
                (draw.drawBoolean() ? assigned : unchanged).add(variable);
            }
            if (assigned.isEmpty()) {
                // A disjunct that changes nothing but the step counter is a stuttering step the
                // checkers explore without learning anything, so one variable always moves. The
                // fallback is byte-free, which keeps it out of the encoding.
                assigned.add(unchanged.removeFirst());
            }

            for (var variable : assigned) {
                conjuncts.add(draw.draw(assignment(variable, remainingDepth)));
            }
            conjuncts.add(builder().primeEq(
                    nameOf(step),
                    builder().plus(nameOf(step), builder().integer(BigInteger.ONE))));
            if (!unchanged.isEmpty()) {
                conjuncts.add(builder().unchanged(unchangedTarget(unchanged)));
            }
            return builder().and(BuilderArrays.expressions(conjuncts));
        };
    }

    /** Returns a generator of one variable's next value, deterministic or chosen from a set. */
    private Generator<TlaEx> assignment(ScopedName variable, int remainingDepth) {
        return draw -> {
            var type = variable.type();
            if (nondeterministic(draw, type)) {
                return builder().in(
                        builder().prime(nameOf(variable)),
                        draw.draw(expression(new SetType(type), remainingDepth - 1)));
            }
            return builder().primeEq(
                    nameOf(variable), draw.draw(expression(type, remainingDepth - 1)));
        };
    }

    /**
     * Draws whether a variable takes its value from a set rather than from an expression.
     *
     * <p>The choice always costs one byte, even where a configuration excludes set types and only
     * one answer is possible, so that excluding sets does not reframe the bytes after it.
     */
    private boolean nondeterministic(Draw draw, IrType type) {
        var chosen = draw.drawBoolean();
        return chosen && typeFactory.isEnabled(new SetType(type));
    }

    /** Returns the operand of UNCHANGED: one variable, or a tuple of them. */
    private TlaEx unchangedTarget(List<ScopedName> unchanged) {
        if (unchanged.size() == 1) {
            return nameOf(unchanged.getFirst());
        }
        return builder().tuple(BuilderArrays.expressions(
                unchanged.stream().map(this::nameOf).toList()));
    }

    private TlaEx nameOf(ScopedName variable) {
        return builder().name(variable.name(), variable.type().toTlaType());
    }
}
