package io.github.tlaplus.hardening.corpus;

import io.github.tlaplus.hardening.common.FileTrees;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Corpus-owned transient storage for one stage during one workflow invocation. */
public final class StageScratch implements AutoCloseable {
    private final Path parentDirectory;
    private final Path runDirectory;
    private boolean closed;

    private StageScratch(Path parentDirectory, Path runDirectory) {
        this.parentDirectory = parentDirectory;
        this.runDirectory = runDirectory;
    }

    static StageScratch create(Path parentDirectory) throws IOException {
        FileTrees.deleteRecursively(parentDirectory);
        Files.createDirectory(parentDirectory);
        try {
            var runDirectory = Files.createTempDirectory(parentDirectory, "run-");
            return new StageScratch(parentDirectory, runDirectory);
        } catch (IOException exception) {
            try {
                FileTrees.deleteRecursively(parentDirectory);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }

    public synchronized Path directory() {
        if (closed) {
            throw new IllegalStateException("stage scratch directory is closed");
        }
        return runDirectory;
    }

    @Override
    public synchronized void close() throws IOException {
        if (!closed) {
            FileTrees.deleteRecursively(parentDirectory);
            closed = true;
        }
    }
}
