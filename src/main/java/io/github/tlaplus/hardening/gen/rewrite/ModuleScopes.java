package io.github.tlaplus.hardening.gen.rewrite;

import io.github.tlaplus.hardening.gen.GeneratedOperator;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.engine.OperandGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apalache_mc.tla.jir.TlaTypes;

/**
 * The names the bodies of a generated module see, as the generator drew them (ADR 0016 §2): the
 * state variables besides {@code step}, {@code step} itself, and the auxiliary operators in
 * declaration order.
 */
record ModuleScopes(List<OperandGenerator.Name> variables, OperandGenerator.Name step,
        List<OperandGenerator.Name> auxiliaries) {
    ModuleScopes {
        variables = List.copyOf(variables);
        Objects.requireNonNull(step, "step");
        auxiliaries = List.copyOf(auxiliaries);
    }

    static ModuleScopes of(GeneratedSpec spec) {
        var variables = new ArrayList<OperandGenerator.Name>();
        OperandGenerator.Name step = null;
        for (var variable : spec.variables()) {
            var name = new OperandGenerator.Name(
                    variable.name(), TlaTypes.typeOf(variable), OperandGenerator.Kind.STATE_VARIABLE);
            if (variable.name().equals(GeneratedSpec.STEP_VARIABLE)) {
                step = name;
            } else {
                variables.add(name);
            }
        }
        var auxiliaries = new ArrayList<OperandGenerator.Name>();
        for (var operator : spec.operators()) {
            if (operator instanceof GeneratedOperator.Auxiliary auxiliary) {
                var declaration = auxiliary.declaration();
                auxiliaries.add(new OperandGenerator.Name(
                        declaration.name(), TlaTypes.typeOf(declaration), OperandGenerator.Kind.DEFINITION));
            }
        }
        return new ModuleScopes(variables, Objects.requireNonNull(step, "step"), auxiliaries);
    }

    /** The scope of the next-state action: the auxiliary operators and the variables but not {@code step}. */
    List<OperandGenerator.Name> action() {
        var scope = new ArrayList<>(auxiliaries);
        scope.addAll(variables);
        return scope;
    }

    /** The scope of the invariant and the property: everything. */
    List<OperandGenerator.Name> state() {
        var scope = action();
        scope.add(step);
        return scope;
    }
}
