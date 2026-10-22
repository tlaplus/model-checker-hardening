package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.GeneratedOperator;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.TemporalProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Reusable coordinator for generating the declarations of one TLA+ module.
 *
 * <p>The engine stores only immutable configuration; every {@link #generate(Draw)} invocation
 * creates its own builder, scope, counters, and name supply, so one engine may be reused and
 * invoked concurrently with distinct cursors.
 *
 * <p>Everything it draws below the declaration level comes from the ordinary expression factory,
 * in the state-level context unless the module layer names another: {@link ActionGenFactory} draws
 * post-assignment guards in the action context and {@link PropertyGenFactory} draws the property in
 * the temporal one. The accounted priming and {@code UNCHANGED} of an action are constructed over
 * the declared variables, which is what lets a generated action account for every variable exactly
 * once; a prime reached through an expression form could sit under a negation or a quantifier and
 * would not, so no expression form primes before the step update.
 */
@ArchRole(Role.FACADE)
@Key
public final class IrSpecGeneratorEngine {
    private final IrGenerationConfig config;

    /**
     * Creates a reusable engine with the supplied generation settings.
     *
     * @param config category exclusions, resource limits, and form weights
     * @throws NullPointerException if {@code config} is {@code null}
     * @throws IllegalArgumentException if the configured weights need more selection slots than
     *     one index can address
     */
    public IrSpecGeneratorEngine(IrGenerationConfig config) {
        // Every body is drawn in the state-level context unless the module layer names another,
        // so no ordinary subexpression primes a name or is temporal, whatever the ignore list.
        this.config = Objects.requireNonNull(config, "config");
        ExpressionKindCatalog.requireAddressableSlots(this.config);
        CustomExpressionKind.requireUsableLibrary(this.config);
    }

    /**
     * Generates the declarations of one module from the remaining bytes of {@code draw}.
     *
     * <p>The remaining input is divided among the {@link ModuleSection}s, and each body is drawn
     * from its own section, so this consumes every remaining byte. A body that runs out of bytes
     * falls back to terminals without affecting what any other body decodes.
     *
     * @param draw cursor supplying every generation choice
     * @throws NullPointerException if {@code draw} is {@code null}
     * @throws InputRejectedException if the decoded choices reach an expected semantic dead end
     * @throws RuntimeException if the builder rejects an internally constructed expression
     */
    public GeneratedSpec generate(Draw draw) {
        Objects.requireNonNull(draw, "draw");
        var sections = ModuleSection.split(draw, config);
        var context = new GenerationContext(config);
        var typeFactory = new IrTypeGenFactory(context);
        var expressionFactory = new IrExprGenFactory(context, typeFactory);
        var depth = config.expressions().maximumExpressionDepth();

        var declared = sections.get(ModuleSection.VARIABLES).draw(
                ModulePrelude.variables(context, typeFactory, List.of()));
        var variables = declared.data();
        var step = declared.step();

        var actions = new ActionGenFactory(
                context, typeFactory, expressionFactory, variables, step);

        var operators = sections.get(ModuleSection.AUXILIARY_OPERATORS)
                .draw(ModulePrelude.auxiliaryOperators(context, typeFactory, expressionFactory));
        var operatorNames = ModulePrelude.bindings(operators);
        // Actions do not read step: a variable assigned from the counter changes on every
        // transition and passes for real state change in the projected exploration metrics.
        var actionScope = new ArrayList<ScopedName>(operatorNames);
        actionScope.addAll(variables);
        var stateScope = new ArrayList<ScopedName>(actionScope);
        stateScope.add(step);

        // Each body owns its bytes, so the invariant no longer has to be drawn first to avoid
        // decoding from an exhausted cursor, and it may apply the auxiliary definitions.
        var invariant = sections.get(ModuleSection.INVARIANT).draw(context.withBindings(
                stateScope,
                context.withFreshNodeBudget(
                        expressionFactory.mkGen(PrimitiveType.BOOL, depth))));

        // Action operators read current state and prime, so they are drawn with the state
        // variables, but not step, in scope. Only Next receives the completed visibility index;
        // Init and the invariant never see action operators.
        var actionOperators = sections.get(ModuleSection.ACTION_OPERATORS).draw(context.withBindings(
                actionScope, actions.actionOperators(depth)));
        var generatedOperators = new ArrayList<GeneratedOperator>();
        operators.forEach(operator -> generatedOperators.add(operator.generated()));
        actionOperators.forEach(operator -> generatedOperators.add(operator.generated()));

        // Init sees the operators but not the variables: a conjunct that read another variable
        // would depend on an evaluation order the predicate does not fix.
        var initPredicate = sections.get(ModuleSection.INIT).draw(context.withBindings(
                operatorNames, context.withFreshNodeBudget(
                        actions.initPredicate(depth, ActionShapeGenFactory.AssignmentForm.ANY, List.of()))));
        var nextAction = sections.get(ModuleSection.NEXT).draw(
                context.withBindings(actionScope, actions.nextAction(depth,
                        new VisibleActionOperators(actionOperators))));

        // The property reads the state and may apply the auxiliary definitions, like the invariant.
        var properties = new PropertyGenFactory(context, typeFactory, expressionFactory);
        var property = ModuleSection.PROPERTY.isPresent(config)
                ? sections.get(ModuleSection.PROPERTY).draw(context.withBindings(
                        stateScope, properties.property(depth)))
                : Optional.<TemporalProperty>empty();
        return new GeneratedSpec(
                declared.declarations(),
                generatedOperators,
                initPredicate,
                nextAction,
                invariant,
                property,
                config.modules().maximumSteps());
    }
}
