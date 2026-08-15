package io.github.tlaplus.hardening.corpus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;

/** Corpus-owned transient storage for one workflow invocation. */
public final class ParserScratch implements AutoCloseable {
    private static final LinkOption[] NO_FOLLOW_LINKS = {LinkOption.NOFOLLOW_LINKS};

    private final Path parentDirectory;
    private final Path runDirectory;
    private boolean closed;

    private ParserScratch(Path parentDirectory, Path runDirectory) {
        this.parentDirectory = parentDirectory;
        this.runDirectory = runDirectory;
    }

    static ParserScratch create(Path parentDirectory) throws IOException {
        deleteRecursively(parentDirectory);
        Files.createDirectory(parentDirectory);
        try {
            var runDirectory = Files.createTempDirectory(parentDirectory, "run-");
            return new ParserScratch(parentDirectory, runDirectory);
        } catch (IOException exception) {
            try {
                deleteRecursively(parentDirectory);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    public synchronized Path directory() {
        if (closed) {
            throw new IllegalStateException("parser scratch directory is closed");
        }
        return runDirectory;
    }

    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            deleteRecursively(parentDirectory);
            closed = true;
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (Files.notExists(root, NO_FOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
