package io.github.tlaplus.hardening.gen.engine;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Semantic role of a name visible during expression generation.
 *
 * <p>The roles differ in how TLA+ labels see them. A label's formal parameters must be exactly
 * the identifiers introduced by expression-level binders whose scope contains it, so only
 * {@link #BINDER} contributes one. A definition's name, a definition's formal parameters and a
 * declared state variable are all in lexical scope without being binders in that sense.
 */
enum ScopedNameKind {
    BINDER,
    DEFINITION,
    STATE_VARIABLE
}

/** A typed name visible while recursively generating a lexical body. */
record ScopedName(String name, IrType type, ScopedNameKind kind) {
    ScopedName {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
    }

    /** Creates a binder introduced by a quantifier, CHOOSE, function, set or lambda construct. */
    static ScopedName binder(String name, IrType type) {
        return new ScopedName(name, type, ScopedNameKind.BINDER);
    }

    /** Creates a definition name or a definition's formal parameter. */
    static ScopedName definition(String name, IrType type) {
        return new ScopedName(name, type, ScopedNameKind.DEFINITION);
    }

    /** Creates a declared state-variable binding. */
    static ScopedName stateVariable(String name, IrType type) {
        return new ScopedName(name, type, ScopedNameKind.STATE_VARIABLE);
    }
}

/** Exception-safe dynamic view over a persistent lexical-scope list. */
final class NameScope {
    private io.vavr.collection.List<ScopedName> current = io.vavr.collection.List.empty();
    private io.vavr.collection.List<ScopedName> binders = io.vavr.collection.List.empty();

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

    /**
     * Returns the formal parameters a label placed here must declare: every binder in scope
     * within the innermost enclosing definition body, innermost first and without repetition.
     *
     * <p>Order does not matter to SANY, but set equality does: a missing name and an extra name
     * are both semantic errors.
     */
    List<String> labelParameters() {
        return binders.distinctBy(ScopedName::name).map(ScopedName::name).asJava();
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
        var added = io.vavr.collection.List.<ScopedName>ofAll(bindings).map(Objects::requireNonNull);
        var previous = current;
        var previousBinders = binders;
        try {
            current = added.appendAll(current);
            binders = added.filter(binding -> binding.kind() == ScopedNameKind.BINDER)
                    .appendAll(binders);
            return body.get();
        } finally {
            current = previous;
            binders = previousBinders;
        }
    }

    /**
     * Runs a computation as the body of a nested definition, which starts a new label scope.
     *
     * <p>A label inside a LET declaration body declares no parameter for a binder enclosing the
     * LET, and declaring one is rejected as an extra parameter. Lexical visibility is deliberately
     * left alone: such a body may still refer to that binder.
     */
    <T> T withDefinitionBoundary(Supplier<? extends T> body) {
        Objects.requireNonNull(body, "body");
        var previousBinders = binders;
        try {
            binders = io.vavr.collection.List.empty();
            return body.get();
        } finally {
            binders = previousBinders;
        }
    }

    /** Filters the visible, shadow-resolved bindings. */
    private List<ScopedName> matching(Predicate<? super ScopedName> predicate) {
        return current.distinctBy(ScopedName::name).filter(predicate).asJava();
    }
}
