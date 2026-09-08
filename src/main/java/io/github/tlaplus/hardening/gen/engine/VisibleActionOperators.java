package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.ActionEffect;
import io.github.tlaplus.hardening.gen.GeneratedActionOperator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Immutable effect index; matching candidates retain declaration order for byte selection. */
final class VisibleActionOperators {
    static final VisibleActionOperators EMPTY = new VisibleActionOperators(List.of());

    /** Only the internal signature decorates the canonical declaration and effect. */
    record Operator(GeneratedActionOperator generated, OperatorType type) {}

    private final Map<ActionEffect, List<Operator>> byEffect;

    VisibleActionOperators(List<Operator> operators) {
        byEffect = operators.stream().collect(Collectors.groupingBy(
                operator -> operator.generated().effect(),
                Collectors.collectingAndThen(Collectors.toList(), List::copyOf)));
    }

    List<Operator> matching(List<ScopedName> variables) {
        return byEffect.getOrDefault(
                new ActionEffect(variables.stream().map(ScopedName::name).toList()), List.of());
    }
}
