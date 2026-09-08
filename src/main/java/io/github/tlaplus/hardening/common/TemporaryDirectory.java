package io.github.tlaplus.hardening.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Owned temporary storage, removed on normal and exceptional completion. */
public record TemporaryDirectory(Path path) implements AutoCloseable {
    public static TemporaryDirectory create(String prefix) throws IOException {
        return new TemporaryDirectory(Files.createTempDirectory(prefix));
    }

    @Override public void close() throws IOException {
        FileTrees.deleteRecursively(path);
    }
}
