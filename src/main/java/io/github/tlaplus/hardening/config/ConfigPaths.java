package io.github.tlaplus.hardening.config;

import java.nio.file.Path;
import java.util.List;

/** Resolution of the file paths a configuration file names. */
final class ConfigPaths {
    private ConfigPaths() {}

    /** Resolves paths against the configuration file's directory, as absolute normalized paths. */
    static List<Path> relativeTo(List<Path> paths, Path directory) {
        return paths.stream()
                .map(path -> directory.resolve(path).toAbsolutePath().normalize())
                .toList();
    }
}
