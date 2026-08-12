package io.github.tlaplus.hardening.gen.engine;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** A typed name visible while recursively generating a lexical body. */
record ScopedName(String name, IrType type) {
    ScopedName {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }
}

/** Exception-safe dynamic view over a persistent lexical-scope list. */
final class NameScope {
    private io.vavr.collection.List<ScopedName> current = io.vavr.collection.List.empty();

    /** Returns visible names of exactly the requested type, innermost scope first. */
    List<ScopedName> matching(IrType type) {
        Objects.requireNonNull(type, "type");
        return current.distinctBy(ScopedName::name)
                .filter(binding -> binding.type().equals(type))
                .asJava();
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
}
