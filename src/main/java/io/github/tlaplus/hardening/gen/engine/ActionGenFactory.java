package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.ActionEffect;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.GeneratedActionOperator;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.engine.ActionShapeGenFactory.AssignmentRequirement;
import io.github.tlaplus.hardening.gen.engine.ActionShapeGenFactory.Request;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Module-level action assembly: Init, operator definitions, bounded parameters, guards, Next and
 * the unconditional step update. Recursive accounting belongs to ActionShapeGenFactory; ordinary
 * expressions exclude action, temporal and exotic syntax and read current state only.
 */
final class ActionGenFactory extends AbstractExprGenFactory {
    private final List<ScopedName> variables;
    private final ScopedName step;
    private final ActionShapeGenFactory shapes;

    ActionGenFactory(GenerationContext context, IrTypeGenFactory types,
                     IrExprGenFactory expressions, List<ScopedName> variables, ScopedName step) {
        super(context, types, expressions);
        this.variables = List.copyOf(variables);
        this.step = step;
        this.shapes = new ActionShapeGenFactory(context, types, expressions);
    }

    /**
     * Draws action operators in dependency order. Each body sees exactly the already generated
     * prefix, never itself or a later operator. No factory-owned registry survives the draw.
     * Operators read current state and account for their nonempty effect, but never mention step.
     */
    Generator<List<VisibleActionOperators.Operator>> actionOperators(int expressionDepth) {
        return draw -> {
            var operators = new ArrayList<VisibleActionOperators.Operator>();
            var maximum = context.config().modules().actions().maximumActionOperators();
            while (operators.size() < maximum && draw.drawBoolean()) {
                var effect = new ArrayList<ScopedName>();
                for (var variable : variables) {
                    if (draw.drawBoolean()) {
                        effect.add(variable);
                    }
                }
                if (effect.isEmpty()) {
                    effect.add(variables.getFirst());
                }
                var argumentTypes = draw.draw(typeFactory.parameterTypes());
                var parameters = context.definitionParameters("actionArg", argumentTypes);
                var name = context.fresh("Act");
                var visiblePrefix = new VisibleActionOperators(operators);
                var body = draw.draw(context.withBindings(parameters.bindings(),
                        context.withDefinitionBoundary(context.withFreshNodeBudget(shapes.shape(
                                request(effect, expressionDepth), visiblePrefix).map(shapes::conjoin)))));
                var declaration = builder().decl(name, body, parameters.declarations());
                var generated = new GeneratedActionOperator(declaration,
                        new ActionEffect(effect.stream().map(ScopedName::name).toList()));
                operators.add(new VisibleActionOperators.Operator(generated,
                        new OperatorType(argumentTypes, PrimitiveType.BOOL)));
            }
            return List.copyOf(operators);
        };
    }

    /** One constraint per variable, drawn without state-variable bindings, followed by step = 0. */
    Generator<TlaEx> initPredicate(int expressionDepth) {
        return draw -> {
            var conjuncts = new ArrayList<TlaEx>();
            for (var variable : variables) {
                var name = shapes.nameOf(variable);
                var type = variable.type();
                conjuncts.add(shapes.nondeterministic(draw, type)
                        ? builder().in(name, draw.draw(expression(new SetType(type), expressionDepth)))
                        : builder().eql(name, draw.draw(expression(type, expressionDepth))));
            }
            conjuncts.add(builder().eql(shapes.nameOf(step), builder().integer(BigInteger.ZERO)));
            return builder().and(BuilderArrays.expressions(conjuncts));
        };
    }

    /** Each complete disjunct receives its own node budget and the same explicit visibility. */
    Generator<TlaEx> nextAction(int expressionDepth, VisibleActionOperators visible) {
        return BasicGenerators.listOf(
                        context.withFreshNodeBudget(action(expressionDepth, visible)),
                        1, context.config().modules().actions().maximumActions())
                .map(disjuncts -> builder().or(BuilderArrays.expressions(disjuncts)));
    }

    /**
     * Parameters nest outermost-first, so each bound sits inside the scope of the parameters
     * drawn before it and a label there must declare them. Drawing the bound in that scope keeps
     * the generated tree's lexical structure the same as the rendered source's. A parameter is
     * still not in scope in its own bound.
     */
    private Generator<TlaEx> action(int expressionDepth, VisibleActionOperators visible) {
        return draw -> {
            var parameters = new ArrayList<Binding>();
            var bounds = new ArrayList<TlaEx>();
            // Excluding sets also excludes parameters, without spending continuation markers.
            var maximumParameters = context.config().ignoredCategories().contains(ExpressionCategory.SET)
                    ? 0 : context.config().modules().actions().maximumActionParameters();
            while (parameters.size() < maximumParameters && draw.drawBoolean()) {
                var type = draw.draw(typeFactory.valueType());
                var enclosing = parameters.stream().map(Binding::name).toList();
                var parameter = freshBinding("actionParam", type);
                parameters.add(parameter);
                bounds.add(draw.draw(context.withBindings(
                        enclosing, expression(new SetType(type), expressionDepth - 1))));
            }
            var body = context.withBindings(parameters.stream().map(Binding::name).toList(),
                    actionBody(expressionDepth, visible));
            var action = draw.draw(body);
            for (var index = parameters.size() - 1; index >= 0; index--) {
                action = builder().exists(parameters.get(index).variable(), bounds.get(index), action);
            }
            return action;
        };
    }

    /**
     * A disjunct is the conjunction of the step guard, the guards, the shape, the step update and
     * the post-assignment guards, in that order. Only the shape accounts for variables.
     *
     * <p>The step guard {@code step < maximumSteps} is byte-free. With the skeleton's stuttering
     * disjunct it closes the state graph at the bound, so both checkers explore the same finite
     * graph and see the same infinite behaviors.
     *
     * <p>Guards are a terminated list of state predicates. Post-assignment guards are a terminated
     * list of predicates drawn in the action context, so they may read primed variables. They come
     * after the step update, where every primed variable has been assigned on every path; the
     * step update is a fixed conjunct, which is how a reader of the IR tells the accounted spine
     * from the post-assignment guards. With the {@code action} category ignored the list is empty
     * and spends no marker.
     *
     * <p>The shape is drawn first: guards are ordinary predicates that a short section can do
     * without, whereas a shape drawn from exhausted bytes is always a leaf and never applies an
     * action operator.
     */
    private Generator<TlaEx> actionBody(int expressionDepth, VisibleActionOperators visible) {
        return draw -> {
            var shape = draw.draw(shapes.shape(request(variables, expressionDepth), visible));
            var guards = draw.draw(BasicGenerators.listOf(
                    expression(PrimitiveType.BOOL, expressionDepth - 1),
                    0, context.config().expressions().collections().maximumSize()));
            var maximumPostGuards = context.config().ignoredCategories().contains(ExpressionCategory.ACTION)
                    ? 0 : context.config().expressions().collections().maximumSize();
            var postGuards = draw.draw(BasicGenerators.listOf(
                    atLevel(LevelContext.ACTION, PrimitiveType.BOOL, expressionDepth - 1),
                    0, maximumPostGuards));
            var conjuncts = new ArrayList<TlaEx>();
            conjuncts.add(builder().lt(shapes.nameOf(step), builder().integer(
                    BigInteger.valueOf(context.config().modules().maximumSteps()))));
            conjuncts.addAll(guards);
            conjuncts.addAll(shape);
            conjuncts.add(stepUpdate());
            conjuncts.addAll(postGuards);
            return builder().and(BuilderArrays.expressions(conjuncts));
        };
    }

    /** Returns {@code step' = step + 1}, the conjunct that ends a disjunct's accounted spine. */
    private TlaEx stepUpdate() {
        return builder().primeEq(shapes.nameOf(step),
                builder().plus(shapes.nameOf(step), builder().integer(BigInteger.ONE)));
    }

    private Request request(List<ScopedName> effect, int expressionDepth) {
        return new Request(effect, context.config().modules().actions().maximumActionDepth(), expressionDepth,
                AssignmentRequirement.REQUIRED);
    }
}
