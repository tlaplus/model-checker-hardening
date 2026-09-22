package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.config.OperatorLibraryConfig;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Copies a library's classpath into a corpus, so that the corpus keeps the release it was
 * initialized with. A file entry is copied as is, together with a sibling {@code VERSION} file
 * that records its provenance; a directory entry as its top-level regular files.
 */
public final class LibraryInstall {
    /** The corpus subdirectory, relative to {@code config.toml}, that holds library sources. */
    public static final Path DIRECTORY = Path.of("tla");

    /** The provenance file copied beside a file entry. */
    private static final String VERSION = "VERSION";

    private LibraryInstall() {}

    /** Returns {@code library} with each classpath entry named as installed, relative to the corpus. */
    public static OperatorLibraryConfig installed(OperatorLibraryConfig library) throws WorkflowException {
        var classpath = new ArrayList<Path>();
        var names = new LinkedHashSet<Path>();
        for (var entry : library.classpath()) {
            if (!Files.exists(entry)) throw new WorkflowException("library classpath entry does not exist: " + entry);
            var installed = DIRECTORY.resolve(entry.getFileName());
            if (!names.add(installed)) throw new WorkflowException("library classpath entries share the name " + installed);
            classpath.add(installed);
        }
        return new OperatorLibraryConfig(classpath, library.modules());
    }

    /** Copies every classpath entry of {@code library} under {@code corpusRoot}, never replacing a file. */
    public static void copy(OperatorLibraryConfig library, Path corpusRoot) throws IOException {
        var target = Files.createDirectories(corpusRoot.resolve(DIRECTORY));
        var versions = new LinkedHashSet<Path>();
        for (var entry : library.classpath()) {
            if (Files.isDirectory(entry)) {
                var directory = Files.createDirectory(target.resolve(entry.getFileName()));
                for (var file : regularFiles(entry)) Files.copy(file, directory.resolve(file.getFileName()));
            } else {
                Files.copy(entry, target.resolve(entry.getFileName()));
                var version = entry.resolveSibling(VERSION);
                if (Files.isRegularFile(version)) versions.add(version);
            }
        }
        for (var version : versions) Files.copy(version, target.resolve(VERSION));
    }

    private static List<Path> regularFiles(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }
}
