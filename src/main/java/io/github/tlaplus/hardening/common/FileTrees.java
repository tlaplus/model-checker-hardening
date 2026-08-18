package io.github.tlaplus.hardening.common;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** File-tree operations shared by the corpus and by isolated worker processes. */
public final class FileTrees {
    private FileTrees() {}

    /**
     * Deletes a tree depth-first without following symbolic links. A missing root is not an
     * error. Deletion is best-effort per entry: a concurrent deletion is tolerated, while any
     * other failure propagates.
     */
    public static void deleteRecursively(Path root) throws IOException {
        if (Files.notExists(root, NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
