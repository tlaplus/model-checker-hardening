package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;

/**
 * Mutable semantic state shared by every component of one generation run.
 *
 * <p>The byte cursor is deliberately not part of this context. Byte-consuming operations return
 * deferred {@link Generator} values, which makes cursor sharing visible at composition sites.
 */
final class GenerationContext {
    private final IrGenerationConfig config;
    private final TlaTypedScopeUncheckedBuilder builder =
            new TlaTypedScopeUncheckedBuilder();
    private final NameScope scope = new NameScope();
    private final Map<IrType, Integer> terminalRotation = new HashMap<>();
    private int nameCount;
    private int fieldCount;
    private int nodeCount;

    GenerationContext(IrGenerationConfig config) {
        this.config = config;
    }

    /** Returns the settings for this generation run. */
    IrGenerationConfig config() {
        return config;
    }

    /** Returns the type-safe builder shared by all expression generator factories. */
    TlaTypedScopeUncheckedBuilder builder() {
        return builder;
    }

    /** Returns a fresh identifier using the supplied prefix. */
    String fresh(String prefix) {
        return prefix + nameCount++;
    }

    /** Creates a fresh typed binding without changing the current scope. */
    ScopedName freshBinding(String prefix, IrType type) {
        return new ScopedName(fresh(prefix), type);
    }

    /** Reports whether an exactly typed binding is currently visible. */
    boolean hasBinding(IrType type) {
        return !scope.matching(type).isEmpty();
    }

    /**
     * Returns the next terminal for this type, rotating over the visible bindings and then the
     * closed terminal, which an empty result stands for.
     *
     * <p>The rotation consumes no bytes, which is what lets byte-free terminal construction use a
     * bound name at all. Returning the innermost binding every time instead would make every
     * starved leaf of a type in one scope the same name, collapsing same-type sibling leaves into
     * tautologies such as {@code x = x} and {@code x \in {x}} that a model checker folds away
     * before reaching anything interesting.
     *
     * <p>The position is kept per type rather than in one counter, because a terminal of an
     * unrelated type would otherwise shift the phase between two same-type siblings and reinstate
     * that collapse about half the time. It advances only when a binding is visible; with nothing
     * to rotate over, leaving it alone keeps the phase meaningful for the scopes that have one.
     */
    Optional<ScopedName> nextTerminalBinding(IrType type) {
        var visible = scope.matching(type);
        if (visible.isEmpty()) {
            return Optional.empty();
        }
        // The closed terminal is the last candidate of every cycle.
        var position = terminalRotation.merge(type, 1, Integer::sum) - 1;
        var choice = Math.floorMod(position, visible.size() + 1);
        return choice == visible.size() ? Optional.empty() : Optional.of(visible.get(choice));
    }

    /** Selects an exactly typed visible binding without inventing a free name. */
    Generator<ScopedName> chooseBinding(IrType type) {
        return chooseScoped(
                () -> scope.matching(type), "no binding of type " + type + " is in scope");
    }

    /** Reports whether a visible operator returns the requested type. */
    boolean hasOperatorReturning(IrType resultType) {
        return !scope.operatorsReturning(resultType).isEmpty();
    }

    /** Selects a visible operator whose result has the requested type. */
    Generator<ScopedName> chooseOperatorReturning(IrType resultType) {
        return chooseScoped(
                () -> scope.operatorsReturning(resultType),
                "no operator returning " + resultType + " is in scope");
    }

    /** Selects a declared state variable or rejects when the scope contains none. */
    Generator<ScopedName> chooseStateVariable() {
        return chooseScoped(scope::stateVariables, "no state variable is in scope");
    }

    /** Returns a generator that runs its body with one additional lexical binding. */
    <T> Generator<T> withBinding(
            ScopedName binding, Generator<? extends T> body) {
        return draw -> scope.withBinding(binding, () -> draw.draw(body));
    }

    /** Returns a generator that runs its body with multiple additional lexical bindings. */
    <T> Generator<T> withBindings(
            List<? extends ScopedName> bindings,
            Generator<? extends T> body) {
        return draw -> scope.withBindings(bindings, () -> draw.draw(body));
    }

    /**
     * Returns a generator that runs its body under a node budget of its own.
     *
     * <p>The budget counts recursive expression requests and is consumed in pre-order, so whatever
     * is drawn last is what falls back to terminals. One budget spanning several independent
     * top-level bodies would therefore let an early body decide how much is left for a later one:
     * a large {@code Init} would starve {@code Inv} into a constant. Each top-level body gets its
     * own budget instead, and the prior count is restored afterwards, including on an exceptional
     * exit, so a rejected body does not leak its consumption into the next one.
     */
    <T> Generator<T> withFreshNodeBudget(Generator<? extends T> body) {
        Objects.requireNonNull(body, "body");
        return draw -> {
            var previous = nodeCount;
            nodeCount = 0;
            try {
                return draw.draw(body);
            } finally {
                nodeCount = previous;
            }
        };
    }

    /** Reports whether another expression request fits in the current node budget, consuming it. */
    boolean consumeNode() {
        return nodeCount++ < config.expressions().maximumNodes();
    }

    /** Returns a fresh record-field identifier. */
    String freshField() {
        return "field" + fieldCount++;
    }

    /** Returns a fresh variant-tag identifier from the shared field supply. */
    String freshTag() {
        return "Tag" + fieldCount++;
    }

    /** Defers candidate lookup until the generator runs inside its intended lexical scope. */
    private Generator<ScopedName> chooseScoped(
            Supplier<List<ScopedName>> candidates, String missingMessage) {
        return draw -> {
            var available = candidates.get();
            if (available.isEmpty()) {
                throw new InputRejectedException(missingMessage);
            }
            return draw.choose(available);
        };
    }
}
