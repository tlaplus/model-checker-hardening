package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.GeneratedOperator;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.Generator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apalache_mc.tla.jir.TlaDeclarations;

/**
 * The declarations every generated module begins with: its state variables and its state-free
 * auxiliary definitions. The module and graph decoders draw them alike, each from its own section.
 */
final class ModulePrelude {
    private ModulePrelude() {}

    /**
     * The declared state variables.
     *
     * @param data the drawn variables, which actions assign and expressions read
     * @param controls fixed variables that only the module layer reads and assigns, such as a graph's
     *     {@code pc}
     * @param step the step counter, declared last
     * @param declarations every variable in declaration order: data, controls, step
     */
    record Variables(List<ScopedName> data, List<ScopedName> controls, ScopedName step,
                     List<TlaVarDecl> declarations) {
        Variables {
            data = List.copyOf(data);
            controls = List.copyOf(controls);
            Objects.requireNonNull(step, "step");
            declarations = List.copyOf(declarations);
        }
    }

    /** One generated definition and the binding through which later expressions apply it. */
    record DefinedOperator(ScopedName binding, GeneratedOperator.Auxiliary generated) {}

    /**
     * Returns a generator of the state variables: a terminated non-empty list of value types named
     * {@code var0} onward, then {@code controls}, then the step counter.
     */
    static Generator<Variables> variables(GenerationContext context, IrTypeGenFactory types,
                                          List<ScopedName> controls) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(types, "types");
        var fixed = List.copyOf(controls);
        return draw -> {
            var drawn = draw.draw(BasicGenerators.listOf(
                    types.valueType(), 1, context.config().modules().maximumVariables()));
            var data = new ArrayList<ScopedName>();
            for (var type : drawn) {
                data.add(ScopedName.stateVariable(context.fresh("var"), type));
            }
            var step = ScopedName.stateVariable(GeneratedSpec.STEP_VARIABLE, PrimitiveType.INT);
            var declarations = new ArrayList<TlaVarDecl>();
            for (var variable : data) {
                declarations.add(declaration(variable));
            }
            for (var control : fixed) {
                declarations.add(declaration(control));
            }
            declarations.add(declaration(step));
            return new Variables(data, fixed, step, declarations);
        };
    }

    /**
     * Returns a generator of the auxiliary definitions, in dependency order.
     *
     * <p>A definition's body sees the definitions before it but no state variable, which is what
     * makes every definition applicable in {@code Init}, {@code Next} and {@code Inv} alike: a
     * definition that read a variable could not be applied in {@code Init}, where no variable has
     * a value yet.
     */
    static Generator<List<DefinedOperator>> auxiliaryOperators(
            GenerationContext context, IrTypeGenFactory types, IrExprGenFactory expressions) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(expressions, "expressions");
        return draw -> {
            var config = context.config();
            var defined = new ArrayList<DefinedOperator>();
            var visible = new ArrayList<ScopedName>();
            var maximum = config.modules().maximumAuxiliaryOperators();
            while (defined.size() < maximum && draw.drawBoolean()) {
                var arguments = draw.draw(types.parameterTypes());
                var result = draw.draw(types.valueType());
                var type = new OperatorType(arguments, result);

                var parameters = context.definitionParameters("parameter", arguments);
                var bindings = new ArrayList<ScopedName>(visible);
                bindings.addAll(parameters.bindings());

                var name = context.fresh("Op");
                var body = draw.draw(context.withBindings(
                        bindings,
                        context.withDefinitionBoundary(context.withFreshNodeBudget(
                                expressions.mkGen(
                                        result, config.expressions().maximumExpressionDepth())))));
                var declaration = context.builder()
                        .decl(name, body, parameters.declarations());
                var binding = ScopedName.definition(name, type);
                visible.add(binding);
                defined.add(new DefinedOperator(binding, new GeneratedOperator.Auxiliary(declaration)));
            }
            return List.copyOf(defined);
        };
    }

    /** Returns the bindings through which later bodies apply {@code operators}. */
    static List<ScopedName> bindings(List<DefinedOperator> operators) {
        return operators.stream().map(DefinedOperator::binding).toList();
    }

    private static TlaVarDecl declaration(ScopedName variable) {
        return TlaDeclarations.variable(variable.name(), variable.type().toTlaType());
    }
}
