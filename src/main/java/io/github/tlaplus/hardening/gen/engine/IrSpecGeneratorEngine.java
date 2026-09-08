package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaOperDecl;
import java.math.BigInteger;
import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.GeneratedActionOperator;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TypedParameter;

/**
 * Reusable coordinator for generating the declarations of one TLA+ module.
 *
 * <p>The engine stores only immutable configuration; every {@link #generate(Draw)} invocation
 * creates its own builder, scope, counters, and name supply, so one engine may be reused and
 * invoked concurrently with distinct cursors.
 *
 * <p>Everything it draws below the declaration level comes from the ordinary expression factory,
 * with the action and temporal categories excluded whatever the caller configured. Priming and
 * {@code UNCHANGED} are constructed by {@link ActionGenFactory} over the declared variables, which
 * is what lets a generated action account for every variable exactly once; a prime reached through
 * an expression form could sit under a negation or a quantifier and would not.
 */
public final class IrSpecGeneratorEngine {
    /**
     * Name of the step counter that bounds exploration. Every disjunct advances it, and the bound
     * predicate constrains it, so a generated module cannot run a checker forever.
     */
    private static final String STEP_VARIABLE = "step";

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
        Objects.requireNonNull(config, "config");
        // The module layer owns every action and temporal construct, so the subexpression decoder
        // never produces one, however the corpus configured its ignore list.
        this.config = config.ignoring(
                ExpressionCategory.ACTION,
                ExpressionCategory.TEMPORAL,
                ExpressionCategory.EXOTIC);
        ExpressionKindCatalog.requireAddressableSlots(this.config);
    }

    /**
     * Generates the declarations of one module from the current position of {@code draw}.
     *
     * @param draw cursor supplying every generation choice
     * @throws NullPointerException if {@code draw} is {@code null}
     * @throws InputRejectedException if the decoded choices reach an expected semantic dead end
     * @throws RuntimeException if the builder rejects an internally constructed expression
     */
    public GeneratedSpec generate(Draw draw) {
        Objects.requireNonNull(draw, "draw");
        var context = new GenerationContext(config);
        var typeFactory = new IrTypeGenFactory(context);
        var expressionFactory = new IrExprGenFactory(context, typeFactory);
        var depth = config.expressions().maximumExpressionDepth();

        var variableTypes = draw.draw(BasicGenerators.listOf(
                typeFactory.valueType(), 1, config.modules().maximumVariables()));
        var variables = new ArrayList<ScopedName>();
        var declarations = new ArrayList<TlaVarDecl>();
        for (var type : variableTypes) {
            var variable = ScopedName.stateVariable(context.fresh("var"), type);
            variables.add(variable);
            declarations.add(
                    TlaDeclarations.variable(variable.name(), type.toTlaType()));
        }
        var step = ScopedName.stateVariable(STEP_VARIABLE, PrimitiveType.INT);
        declarations.add(
                TlaDeclarations.variable(step.name(), PrimitiveType.INT.toTlaType()));

        var actions = new ActionGenFactory(
                context, typeFactory, expressionFactory, variables, step);

        // The invariant is drawn first among the bodies, because it is the one that degrades
        // worst when the cursor runs out: a starved definition or action is still a legal one,
        // whereas a starved Boolean decodes to the closed terminal FALSE, and a constantly false
        // invariant is violated by every initial state, which no checker explores past. Drawing
        // it first took that shape from 69% of property-based inputs to 17%. The price is that
        // it cannot apply the auxiliary definitions, which are not yet drawn; Init and Next
        // still can.
        var variableScope = new ArrayList<ScopedName>(variables);
        variableScope.add(step);
        var invariant = draw.draw(context.withBindings(
                variableScope,
                context.withFreshNodeBudget(
                        expressionFactory.mkGen(PrimitiveType.BOOL, depth))));

        var operators = draw.draw(auxiliaryOperators(context, typeFactory, expressionFactory));
        var operatorNames = operators.stream().map(DefinedOperator::binding).toList();
        var stateScope = new ArrayList<ScopedName>(operatorNames);
        stateScope.addAll(variables);
        stateScope.add(step);

        // Action operators read current state and prime, so they are drawn with the state
        // variables in scope. Populating the factory's registry here is what lets Next apply them;
        // Init and the invariant never see them.
        var actionOperators = draw.draw(context.withBindings(
                stateScope, actions.actionOperators(depth)));
        var generatedActionOperators = actionOperators.stream()
                .map(operator -> new GeneratedActionOperator(
                        operator.declaration(),
                        operator.effect().stream().map(ScopedName::name).toList()))
                .toList();

        // Init sees the operators but not the variables: a conjunct that read another variable
        // would depend on an evaluation order the predicate does not fix.
        var initPredicate = draw.draw(context.withBindings(
                operatorNames, context.withFreshNodeBudget(actions.initPredicate(depth))));
        var nextAction = draw.draw(
                context.withBindings(stateScope, actions.nextAction(depth)));

        var boundPredicate = context.builder().le(
                context.builder().name(step.name(), PrimitiveType.INT.toTlaType()),
                context.builder().integer(
                        BigInteger.valueOf(config.modules().maximumSteps())));
        return new GeneratedSpec(
                declarations,
                operators.stream().map(DefinedOperator::declaration).toList(),
                generatedActionOperators,
                initPredicate,
                nextAction,
                invariant,
                boundPredicate,
                config.modules().maximumSteps());
    }

    /** One generated definition and the binding through which later expressions apply it. */
    private record DefinedOperator(
            ScopedName binding, TlaOperDecl declaration) {}

    /**
     * Returns a generator of the auxiliary definitions, in dependency order.
     *
     * <p>A definition's body sees the definitions before it but no state variable, which is what
     * makes every definition applicable in {@code Init}, {@code Next} and {@code Inv} alike: a
     * definition that read a variable could not be applied in {@code Init}, where no variable has
     * a value yet.
     */
    private Generator<List<DefinedOperator>> auxiliaryOperators(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory) {
        return draw -> {
            var defined = new ArrayList<DefinedOperator>();
            var visible = new ArrayList<ScopedName>();
            var maximum = config.modules().maximumAuxiliaryOperators();
            while (defined.size() < maximum && draw.drawBoolean()) {
                var arguments = draw.draw(BasicGenerators.listOf(
                        typeFactory.valueType(),
                        0,
                        config.expressions().maximumCollectionSize()));
                var result = draw.draw(typeFactory.valueType());
                var type = new OperatorType(arguments, result);

                var parameters = new ArrayList<TypedParameter>();
                var bindings = new ArrayList<ScopedName>(visible);
                for (var argument : arguments) {
                    var parameter = context.freshBinding("parameter", argument);
                    bindings.add(parameter);
                    parameters.add(
                            context.builder().param(parameter.name(), argument.toTlaType()));
                }

                var name = context.fresh("Op");
                var body = draw.draw(context.withBindings(
                        bindings,
                        context.withFreshNodeBudget(expressionFactory.mkGen(
                                result, config.expressions().maximumExpressionDepth()))));
                var declaration = context.builder()
                        .decl(name, body, parameters.toArray(TypedParameter[]::new));
                var binding = new ScopedName(name, type);
                visible.add(binding);
                defined.add(new DefinedOperator(binding, declaration));
            }
            return defined;
        };
    }
}
