package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.gen.library.OperatorId;
import io.github.tlaplus.hardening.workflow.worker.StandardModuleResources;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

/** Materializes an ordered module search path as one source-only snapshot for SANY. */
final class LibrarySources {
    static final int MAXIMUM_SOURCE_BYTES = 32 * 1024 * 1024;
    private int remaining = MAXIMUM_SOURCE_BYTES;
    private final Path directory;
    private final Set<String> standardModules;

    private LibrarySources(Path directory, Set<String> standardModules) {
        this.directory = directory;
        this.standardModules = standardModules;
    }

    static void snapshot(List<Path> classpath, Path directory, Path distribution) throws IOException {
        var snapshot = new LibrarySources(directory, standardModules(distribution));
        for (var path : classpath) {
            if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException();
            if (Files.isDirectory(path)) snapshot.directory(path);
            else if (Files.isRegularFile(path)) snapshot.archive(path);
            else throw new IOException("TLA+ classpath entry does not exist: " + path);
        }
    }

    private static Set<String> standardModules(Path distribution) throws IOException {
        try (var zip = new ZipFile(distribution.toFile())) {
            return zip.stream().map(java.util.zip.ZipEntry::getName)
                    .filter(name -> name.startsWith(StandardModuleResources.PREFIX) && name.endsWith(".tla"))
                    .map(name -> name.substring(StandardModuleResources.PREFIX.length()))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    private void directory(Path source) throws IOException {
        try (var files = Files.list(source)) {
            for (var file : files.filter(Files::isRegularFile).sorted().toList()) {
                var name = file.getFileName().toString();
                if (!accepts(name)) continue;
                try (var stream = Files.newInputStream(file)) { save(name, stream); }
            }
        }
    }

    private void archive(Path source) throws IOException {
        try (var zip = new ZipFile(source.toFile())) {
            // SANY's classpath lookup tries StandardModules resources before root resources.
            var order = java.util.Comparator.comparing(
                    (java.util.zip.ZipEntry entry) -> !entry.getName().startsWith(StandardModuleResources.PREFIX))
                    .thenComparing(java.util.zip.ZipEntry::getName);
            for (var entry : zip.stream().sorted(order).toList()) {
                var name = entry.getName();
                if (name.startsWith(StandardModuleResources.PREFIX)) name = name.substring(StandardModuleResources.PREFIX.length());
                if (!entry.isDirectory() && accepts(name)) {
                    try (var stream = zip.getInputStream(entry)) { save(name, stream); }
                }
            }
        } catch (IOException exception) {
            throw new IOException("cannot read TLA+ classpath archive " + source + ": " + exception.getMessage(), exception);
        }
    }

    private boolean accepts(String name) {
        if (name.contains("/") || name.contains("\\") || !name.endsWith(".tla")) return false;
        // Keep tool standard modules wired to the pinned distribution, never to user overrides.
        if (standardModules.contains(name)) return false;
        return !Files.exists(directory.resolve(name));
    }

    private void save(String name, InputStream stream) throws IOException {
        OperatorId.requireIdentifier(name.substring(0, name.length() - 4));
        var bytes = stream.readNBytes(remaining + 1);
        if (bytes.length > remaining) throw new IOException("TLA+ source classpath exceeds " + MAXIMUM_SOURCE_BYTES + " bytes");
        remaining -= bytes.length;
        Files.write(directory.resolve(name), bytes);
    }
}
