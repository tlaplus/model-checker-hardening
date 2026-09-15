package io.github.tlaplus.hardening.corpus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * An entry file in one of the corpus's entry directories, listed but not read.
 *
 * @param location the entry directory that holds the file
 * @param digest the payload digest that names the file
 * @param path the file
 */
public record StoredEntry(CorpusPath location, String digest, Path path) {
    public StoredEntry {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(digest, "digest");
        Objects.requireNonNull(path, "path");
    }

    /**
     * Reads the file's bytes under the corpus's file policy: it must be a regular file, and a
     * symbolic link is not followed.
     *
     * @throws java.nio.file.NoSuchFileException if the file no longer exists
     * @throws CorpusException if the path is not a regular file
     */
    public byte[] read() throws IOException, CorpusException {
        return CorpusLayout.readRegularFile(path, "corpus entry");
    }
}
