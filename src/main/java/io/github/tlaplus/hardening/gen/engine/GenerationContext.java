package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.List;
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

    /**
     * Selects a compatible scoped name or a fresh-name alternative with the cursor's balanced
     * modulo mapping. The fresh identifier is allocated only when its final alternative is chosen;
     * with no compatible scoped names, no selection byte is consumed.
     */
    Generator<String> chooseName(IrType type, String freshPrefix) {
        return draw -> {
            var scoped = scope.matching(type);
            var selected = Math.toIntExact(draw.drawLong(0, scoped.size()));
            return selected == scoped.size()
                    ? fresh(freshPrefix)
                    : scoped.get(selected).name();
        };
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
}
