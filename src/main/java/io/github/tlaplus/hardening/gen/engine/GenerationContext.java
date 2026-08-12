package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.List;
import java.util.function.Supplier;
import org.apalache_mc.tla.jir.TlaCheckedBuilder;

/**
 * Mutable semantic state shared by every component of one generation run.
 *
 * <p>The byte cursor is deliberately not part of this context. Byte-consuming operations return
 * deferred {@link Generator} values, which makes cursor sharing visible at composition sites.
 */
final class GenerationContext {
    private final IrGenerationConfig config;
    private final TlaCheckedBuilder builder = new TlaCheckedBuilder();
    private final NameScope scope = new NameScope();
    private int nameCount;
    private int fieldCount;

    GenerationContext(IrGenerationConfig config) {
        this.config = config;
    }

    /** Returns the limits for this generation run. */
    IrGenerationConfig config() {
        return config;
    }

    /** Returns the checked builder shared by all expression generator factories. */
    TlaCheckedBuilder builder() {
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
