package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.Preconditions;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The identity of one logical corpus entry: the file name it keeps in every stage directory it
 * passes through.
 *
 * <p>A stage moves an entry between directories, so a {@link Path} names a location and not an
 * entry. Anything that tracks an entry across stages — the startup scan and the running workflow
 * both do — keys on this instead.
 */
public record EntryName(String value) {
    public EntryName {
        Objects.requireNonNull(value, "value");
        Preconditions.require(!value.isBlank(), "an entry name must not be blank");
    }

    /** Returns the entry a path names, wherever the path points. */
    public static EntryName of(Path path) {
        return new EntryName(Objects.requireNonNull(path, "path").getFileName().toString());
    }

    @Override
    public String toString() {
        return value;
    }
}
