package io.github.tlaplus.hardening.gen.engine;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Semantic role of a name visible during expression generation. */
enum ScopedNameKind {
    BOUND,
    STATE_VARIABLE
}

/** A typed name visible while recursively generating a lexical body. */
record ScopedName(String name, IrType type, ScopedNameKind kind) {
    /** Creates an ordinary lexical binding. */
    ScopedName(String name, IrType type) {
        this(name, type, ScopedNameKind.BOUND);
    }

    ScopedName {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
    }

    /** Creates a declared state-variable binding. */
    static ScopedName stateVariable(String name, IrType type) {
        return new ScopedName(name, type, ScopedNameKind.STATE_VARIABLE);
    }
}

/** Exception-safe dynamic view over a persistent lexical-scope list. */
final class NameScope {
    private io.vavr.collection.List<ScopedName> current = io.vavr.collection.List.empty();

    /** Returns visible names of exactly the requested type, innermost scope first. */
    List<ScopedName> matching(IrType type) {
        Objects.requireNonNull(type, "type");
        return matching(binding -> binding.type().equals(type));
    }

    /** Returns visible operator names whose result has the requested type. */
    List<ScopedName> operatorsReturning(IrType resultType) {
        Objects.requireNonNull(resultType, "resultType");
        return matching(binding -> binding.type() instanceof OperatorType operatorType
                && operatorType.result().equals(resultType));
    }

    /** Returns visible declared state variables, innermost scope first. */
    List<ScopedName> stateVariables() {
        return matching(binding -> binding.kind() == ScopedNameKind.STATE_VARIABLE);
    }

    /** Runs a computation with one additional visible binding. */
    <T> T withBinding(ScopedName binding, Supplier<? extends T> body) {
        return withBindings(List.of(binding), body);
    }

    /** Runs a computation with additional bindings and restores the prior scope afterward. */
    <T> T withBindings(
            List<? extends ScopedName> bindings, Supplier<? extends T> body) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(body, "body");
        var previous = current;
        try {
            current = io.vavr.collection.List.<ScopedName>ofAll(bindings)
                    .map(Objects::requireNonNull)
                    .appendAll(current);
            return body.get();
        } finally {
            current = previous;
        }
    }

    /** Filters the visible, shadow-resolved bindings. */
    private List<ScopedName> matching(Predicate<? super ScopedName> predicate) {
        return current.distinctBy(ScopedName::name).filter(predicate).asJava();
    }
}
