package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.GeneratedActionOperator;
import java.util.List;

/** Immutable operator prefix; candidates retain declaration order for byte selection. */
final class VisibleActionOperators {
    static final VisibleActionOperators EMPTY = new VisibleActionOperators(List.of());

    /** Only the internal signature decorates the canonical declaration and effect. */
    record Operator(GeneratedActionOperator generated, OperatorType type) {}

    private final List<Operator> operators;

    VisibleActionOperators(List<Operator> operators) {
        this.operators = List.copyOf(operators);
    }

    /**
     * Returns the operators whose effect lies within the requested variables, in declaration order.
     * A caller applying one accounts for the rest of the request separately.
     */
    List<Operator> within(List<ScopedName> variables) {
        var names = variables.stream().map(ScopedName::name).toList();
        return operators.stream()
                .filter(operator -> operator.generated().effect().isWithin(names))
                .toList();
    }
}
