package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Role;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code [tools]} table (ADR 0025 §3): the tool JARs a corpus checks and types with. A tool
 * without a JAR runs the release staged with FuzzTLA, as every corpus did before ADR 0025.
 *
 * @param jars the JAR of each configured tool
 */
@ArchRole(Role.CONFIG)
public record ToolsConfig(Map<ToolJar, Path> jars) {
    /** The corpus subdirectory, relative to {@code config.toml}, that {@code init} copies the JARs into. */
    public static final Path DIRECTORY = Path.of("tools");

    public ToolsConfig {
        var copy = new EnumMap<ToolJar, Path>(ToolJar.class);
        copy.putAll(Objects.requireNonNull(jars, "jars"));
        jars = Map.copyOf(copy);
    }

    /** No configured JAR: every tool runs the staged release. */
    public static ToolsConfig none() {
        return new ToolsConfig(Map.of());
    }

    /** Every tool's JAR at its place in {@link #DIRECTORY}, relative to the configuration file. */
    public static ToolsConfig installed() {
        var jars = new EnumMap<ToolJar, Path>(ToolJar.class);
        for (var tool : ToolJar.values()) {
            jars.put(tool, DIRECTORY.resolve(tool.fileName()));
        }
        return new ToolsConfig(jars);
    }

    /** Returns the configured JAR of {@code tool}. */
    public Optional<Path> jar(ToolJar tool) {
        return Optional.ofNullable(jars.get(Objects.requireNonNull(tool, "tool")));
    }

    /** Resolves the JARs relative to the directory of the configuration file. */
    ToolsConfig relativeTo(Path directory) {
        var resolved = new EnumMap<ToolJar, Path>(ToolJar.class);
        jars.forEach((tool, jar) -> resolved.put(tool, directory.resolve(jar).toAbsolutePath().normalize()));
        return new ToolsConfig(resolved);
    }
}
