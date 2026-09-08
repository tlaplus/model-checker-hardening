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
                var argumentTypes = draw.draw(BasicGenerators.listOf(typeFactory.valueType(),
                        0, context.config().expressions().maximumCollectionSize()));
                var parameters = context.parameters("actionArg", argumentTypes);
                var name = context.fresh("Act");
                var visiblePrefix = new VisibleActionOperators(operators);
                var body = draw.draw(context.withBindings(parameters.bindings(),
                        context.withFreshNodeBudget(shapes.shape(
                                request(effect, expressionDepth), visiblePrefix).map(shapes::conjoin))));
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

    /** Bounds belong to the enclosing scope; parameters are visible only inside the action. */
    private Generator<TlaEx> action(int expressionDepth, VisibleActionOperators visible) {
        return draw -> {
            var parameters = new ArrayList<Binding>();
            var bounds = new ArrayList<TlaEx>();
            // Excluding sets also excludes parameters, without spending continuation markers.
            var maximumParameters = context.config().ignoredCategories().contains(ExpressionCategory.SET)
                    ? 0 : context.config().modules().actions().maximumActionParameters();
            while (parameters.size() < maximumParameters && draw.drawBoolean()) {
                var type = draw.draw(typeFactory.valueType());
                var parameter = freshBinding("actionParam", type);
                parameters.add(parameter);
                bounds.add(draw.draw(expression(new SetType(type), expressionDepth - 1)));
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

    /** Guards are a terminated list; they and the unconditional step update sit outside the shape. */
    private Generator<TlaEx> actionBody(int expressionDepth, VisibleActionOperators visible) {
        return draw -> {
            var conjuncts = new ArrayList<TlaEx>(draw.draw(BasicGenerators.listOf(
                    expression(PrimitiveType.BOOL, expressionDepth - 1),
                    0, context.config().expressions().maximumCollectionSize())));
            conjuncts.addAll(draw.draw(shapes.shape(request(variables, expressionDepth), visible)));
            conjuncts.add(builder().primeEq(shapes.nameOf(step),
                    builder().plus(shapes.nameOf(step), builder().integer(BigInteger.ONE))));
            return builder().and(BuilderArrays.expressions(conjuncts));
        };
    }

    private Request request(List<ScopedName> effect, int expressionDepth) {
        return new Request(effect, context.config().modules().actions().maximumActionDepth(), expressionDepth,
                AssignmentRequirement.REQUIRED);
    }
}
