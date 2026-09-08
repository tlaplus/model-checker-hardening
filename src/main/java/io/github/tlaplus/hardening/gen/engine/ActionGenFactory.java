package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import java.math.BigInteger;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.Generator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apalache_mc.tla.jir.TypedParameter;

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
    private final List<ActionOperator> actionOperatorRegistry = new ArrayList<>();

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
     * One generated action operator and the effect its body accounts for. The type records the
     * declared signature so a call site can name it; the effect is the exact set of variables the
     * body assigns or leaves {@code UNCHANGED} on every path.
     */
    record ActionOperator(TlaOperDecl declaration, OperatorType type, List<ScopedName> effect) {
        String name() {
            return declaration.name();
        }
    }

    /**
     * Returns a generator of the action operators, in dependency order, populating the registry the
     * action shape's {@code CALL} kind draws from.
     *
     * <p>Unlike an auxiliary operator, an action operator's body is a recursive action shape over a
     * chosen non-empty subset of the declared variables — its effect — and reads current state, so
     * it is applicable only in {@code Next}. Each operator sees the ones before it, so a later
     * operator may apply an earlier one; none applies itself. The operators never mention
     * {@code step}: {@code actionBody} advances it once per disjunct, outside every shape and call.
     */
    Generator<List<ActionOperator>> actionOperators(int remainingDepth) {
        return draw -> {
            var maximum = context.config().modules().maximumActionOperators();
            while (actionOperatorRegistry.size() < maximum && draw.drawBoolean()) {
                var effect = new ArrayList<ScopedName>();
                for (var variable : variables) {
                    if (draw.drawBoolean()) {
                        effect.add(variable);
                    }
                }
                if (effect.isEmpty()) {
                    // An operator that accounts for nothing is unusable; the fallback is byte-free.
                    effect.add(variables.getFirst());
                }
                var argumentTypes = draw.draw(BasicGenerators.listOf(
                        typeFactory.valueType(),
                        0,
                        context.config().expressions().maximumCollectionSize()));
                var parameters = new ArrayList<TypedParameter>();
                var parameterNames = new ArrayList<ScopedName>();
                for (var argumentType : argumentTypes) {
                    var parameter = context.freshBinding("actionArg", argumentType);
                    parameterNames.add(parameter);
                    parameters.add(builder().param(parameter.name(), argumentType.toTlaType()));
                }
                var name = context.fresh("Act");
                // The body is a shape over the effect, collapsed to one expression: a bare
                // conjunct, a conjunction, or a top-level disjunction, IF-THEN-ELSE, or call.
                var body = draw.draw(context.withBindings(
                        parameterNames,
                        context.withFreshNodeBudget(
                                actionShape(effect, maximumActionDepth(), remainingDepth, true)
                                        .map(this::conjoin))));
                var type = new OperatorType(argumentTypes, PrimitiveType.BOOL);
                var declaration =
                        builder().decl(name, body, parameters.toArray(TypedParameter[]::new));
                actionOperatorRegistry.add(
                        new ActionOperator(declaration, type, List.copyOf(effect)));
            }
            return List.copyOf(actionOperatorRegistry);
        };
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
                        context.config().modules().maximumActions())
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
                    : context.config().modules().maximumActionParameters();
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

    /**
     * Returns a generator of the guards, the recursive action shape over the declared variables,
     * and the unconditional {@code step' = step + 1}.
     *
     * <p>The step advance and the guards sit outside the shape, so the shape's only job is to
     * account for the declared variables exactly once, however deeply it nests. Zero or more
     * guards are drawn as a terminated list rather than one optional predicate: a hand-written
     * action often has several guard conjuncts, and a byte mutation to one then leaves the rest in
     * place. Each guard is an ordinary current-state Boolean, so none affects the accounting.
     */
    private Generator<TlaEx> actionBody(int remainingDepth) {
        return draw -> {
            var conjuncts = new ArrayList<TlaEx>(draw.draw(BasicGenerators.listOf(
                    expression(PrimitiveType.BOOL, remainingDepth - 1),
                    0,
                    context.config().expressions().maximumCollectionSize())));
            conjuncts.addAll(draw.draw(actionShape(
                    variables, maximumActionDepth(), remainingDepth, true)));
            conjuncts.add(builder().primeEq(
                    nameOf(step),
                    builder().plus(nameOf(step), builder().integer(BigInteger.ONE))));
            return builder().and(BuilderArrays.expressions(conjuncts));
        };
    }

    /** The nesting kinds one action disjunct may take, in fixed byte-decoder order. */
    private enum ShapeKind {
        LEAF,
        CONJUNCTION,
        DISJUNCTION,
        ITE,
        CALL
    }

    /**
     * Draws a shape kind from the fixed catalog with a chain of Boolean markers, biased towards
     * {@code LEAF}. The number of markers read on a path never depends on which kinds are usable
     * here, so remapping an unusable kind to {@code LEAF} at the call site cannot reframe the bytes
     * after it.
     */
    private static ShapeKind drawShapeKind(Draw draw) {
        if (!draw.drawBoolean()) {
            return ShapeKind.LEAF;
        }
        if (!draw.drawBoolean()) {
            return ShapeKind.CONJUNCTION;
        }
        if (!draw.drawBoolean()) {
            return ShapeKind.DISJUNCTION;
        }
        if (!draw.drawBoolean()) {
            return ShapeKind.ITE;
        }
        return ShapeKind.CALL;
    }

    private int maximumActionDepth() {
        return context.config().modules().maximumActionDepth();
    }

    /**
     * Returns a generator of the conjuncts that account for {@code vars} exactly once, inductively.
     * The list is spliced into the enclosing conjunction, so {@code LEAF} and {@code CONJUNCTION}
     * stay flat.
     *
     * <p>{@code vars} is always non-empty. Recursion falls back to a leaf once the depth, the
     * per-disjunct node budget, or the input runs out, mirroring the expression factory's
     * termination.
     *
     * <p>{@code mustBeAssigned} is the obligation that every execution path through this shape
     * assign at least one variable — never leave all of {@code vars} {@code UNCHANGED}. It is set
     * at the two roots ({@link #actionBody} for a {@code Next} disjunct, {@link #actionOperators}
     * for an operator body), because a fragment that changes nothing but the step counter is a
     * stuttering step both checkers explore without learning anything. It threads down so the
     * guarantee still holds after nesting: {@code DISJUNCTION} and {@code ITE} pass it unchanged to
     * both arms, since each is a full path; {@code CONJUNCTION} passes it to the spine group only
     * and clears it for the sibling, since the groups are conjoined and one assigned group
     * suffices; a {@code CALL} needs no flag because the callee body was generated with the
     * obligation already.
     */
    private Generator<List<TlaEx>> actionShape(
            List<ScopedName> vars, int depth, int remainingDepth, boolean mustBeAssigned) {
        return draw -> {
            if (depth <= 0 || !context.consumeNode() || draw.isEmpty()) {
                return leaf(draw, vars, remainingDepth, mustBeAssigned);
            }
            return switch (drawShapeKind(draw)) {
                case LEAF -> leaf(draw, vars, remainingDepth, mustBeAssigned);
                case CONJUNCTION -> vars.size() < 2
                        ? leaf(draw, vars, remainingDepth, mustBeAssigned)
                        : conjunctionShape(draw, vars, depth, remainingDepth, mustBeAssigned);
                case DISJUNCTION -> disjunctionShape(draw, vars, depth, remainingDepth, mustBeAssigned);
                case ITE -> iteShape(draw, vars, depth, remainingDepth, mustBeAssigned);
                case CALL -> matchingActionOperators(effectNameSet(vars)).isEmpty()
                        ? leaf(draw, vars, remainingDepth, mustBeAssigned)
                        : callShape(draw, vars, remainingDepth);
            };
        };
    }

    /**
     * Applies a visible action operator whose effect is exactly {@code vars}. The arguments are
     * drawn from the operator's declared signature; the primed assignments live in its body, which
     * the caller inlines conceptually when it accounts for the disjunct.
     */
    private List<TlaEx> callShape(Draw draw, List<ScopedName> vars, int remainingDepth) {
        var operator = draw.choose(matchingActionOperators(effectNameSet(vars)));
        var arguments = operator.type().arguments().stream()
                .map(argumentType -> draw.draw(expression(argumentType, remainingDepth - 1)))
                .toArray(TlaEx[]::new);
        return List.of(builder().operApply(
                builder().name(operator.name(), operator.type().toTlaType()), arguments));
    }

    /** Returns the visible action operators whose effect set matches {@code effectNames}. */
    private List<ActionOperator> matchingActionOperators(Set<String> effectNames) {
        return actionOperatorRegistry.stream()
                .filter(operator -> effectNameSet(operator.effect()).equals(effectNames))
                .toList();
    }

    private static Set<String> effectNameSet(List<ScopedName> names) {
        return names.stream()
                .map(ScopedName::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * Partitions {@code vars} into assignments and one completing {@code UNCHANGED}: one Boolean
     * per variable sends it to {@code assigned} ({@code v' = e} or {@code v' \in S}) or to
     * {@code unchanged}.
     *
     * <p>This is the only place {@code mustBeAssigned} is consulted. When it holds and the partition
     * left {@code assigned} empty, one variable is moved back, so the leaf assigns something rather
     * than being a pure stutter step. The move is byte-free, which keeps it out of the encoding;
     * when {@code mustBeAssigned} is clear (a non-spine {@code CONJUNCTION} group) an all-{@code UNCHANGED}
     * leaf is left as drawn.
     */
    private List<TlaEx> leaf(
            Draw draw, List<ScopedName> vars, int remainingDepth, boolean mustBeAssigned) {
        var assigned = new ArrayList<ScopedName>();
        var unchanged = new ArrayList<ScopedName>();
        for (var variable : vars) {
            (draw.drawBoolean() ? assigned : unchanged).add(variable);
        }
        if (mustBeAssigned && assigned.isEmpty()) {
            assigned.add(unchanged.removeFirst());
        }
        var conjuncts = new ArrayList<TlaEx>();
        for (var variable : assigned) {
            conjuncts.add(draw.draw(assignment(variable, remainingDepth)));
        }
        if (!unchanged.isEmpty()) {
            conjuncts.add(builder().unchanged(unchangedTarget(unchanged)));
        }
        return conjuncts;
    }

    /**
     * Splits {@code vars} into two non-empty groups, each recursively shaped and spliced flat. The
     * groups are conjoined, so one assigned group satisfies the whole shape: {@code groupA} (the
     * spine) inherits {@code mustBeAssigned} and {@code groupB} is shaped with it cleared, which
     * lets a sibling group be left entirely {@code UNCHANGED}.
     */
    private List<TlaEx> conjunctionShape(
            Draw draw, List<ScopedName> vars, int depth, int remainingDepth, boolean mustBeAssigned) {
        var groupA = new ArrayList<ScopedName>();
        var groupB = new ArrayList<ScopedName>();
        for (var variable : vars) {
            (draw.drawBoolean() ? groupA : groupB).add(variable);
        }
        if (groupA.isEmpty()) {
            groupA.add(groupB.removeFirst());
        } else if (groupB.isEmpty()) {
            groupB.add(groupA.removeLast());
        }
        var conjuncts = new ArrayList<TlaEx>(
                draw.draw(actionShape(groupA, depth - 1, remainingDepth, mustBeAssigned)));
        conjuncts.addAll(draw.draw(actionShape(groupB, depth - 1, remainingDepth, false)));
        return conjuncts;
    }

    /**
     * Two arms, each independently shaping the full {@code vars} and each inheriting
     * {@code mustBeAssigned}, since either arm is a complete execution path.
     */
    private List<TlaEx> disjunctionShape(
            Draw draw, List<ScopedName> vars, int depth, int remainingDepth, boolean mustBeAssigned) {
        var armA = conjoin(draw.draw(actionShape(vars, depth - 1, remainingDepth, mustBeAssigned)));
        var armB = conjoin(draw.draw(actionShape(vars, depth - 1, remainingDepth, mustBeAssigned)));
        return List.of(builder().or(armA, armB));
    }

    /**
     * A current-state predicate and two branches, each independently shaping the full {@code vars}
     * and each inheriting {@code mustBeAssigned}, since either branch is a complete execution path.
     */
    private List<TlaEx> iteShape(
            Draw draw, List<ScopedName> vars, int depth, int remainingDepth, boolean mustBeAssigned) {
        var predicate = draw.draw(expression(PrimitiveType.BOOL, remainingDepth - 1));
        var whenTrue = conjoin(draw.draw(actionShape(vars, depth - 1, remainingDepth, mustBeAssigned)));
        var whenFalse = conjoin(draw.draw(actionShape(vars, depth - 1, remainingDepth, mustBeAssigned)));
        return List.of(builder().ite(predicate, whenTrue, whenFalse));
    }

    /** Wraps a conjunct list as one Boolean expression, without a single-argument conjunction. */
    private TlaEx conjoin(List<TlaEx> conjuncts) {
        return conjuncts.size() == 1
                ? conjuncts.getFirst()
                : builder().and(BuilderArrays.expressions(conjuncts));
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
