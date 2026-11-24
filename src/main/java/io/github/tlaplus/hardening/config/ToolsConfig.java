package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Role;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The tools a corpus checks and types with: the JARs of the {@code [tools]} table (ADR 0025 §3),
 * and the generic checker of the {@code [generic]} table (ADR 0033). A tool without a JAR runs the
 * release staged with FuzzTLA, as every corpus did before ADR 0025.
 *
 * @param jars the JAR of each configured tool
 * @param generic the generic checker, if the corpus configures one
 */
@ArchRole(Role.CONFIG)
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public record ToolsConfig(Map<ToolJar, Path> jars, Optional<GenericCheckerConfig> generic) {
    /**
     * The corpus subdirectory, relative to {@code config.toml}, that {@code init} copies the JARs and
     * the generic checker's executable into.
     */
    public static final Path DIRECTORY = Path.of("tools");

    public ToolsConfig {
        var copy = new EnumMap<ToolJar, Path>(ToolJar.class);
        copy.putAll(Objects.requireNonNull(jars, "jars"));
        jars = Map.copyOf(copy);
        Objects.requireNonNull(generic, "generic");
    }

    /** No configured JAR and no generic checker: every tool runs the staged release. */
    public static ToolsConfig none() {
        return new ToolsConfig(Map.of(), Optional.empty());
    }

    /**
     * Every tool's JAR, and the generic checker's executable, at its place in {@link #DIRECTORY},
     * relative to the configuration file.
     */
    public ToolsConfig installed() {
        var installed = new EnumMap<ToolJar, Path>(ToolJar.class);
        for (var tool : ToolJar.values()) {
            installed.put(tool, DIRECTORY.resolve(tool.fileName()));
        }
        return new ToolsConfig(installed, generic.map(checker -> checker.withExecutable(installedExecutable(checker))));
    }

    /** Returns where {@code init} copies the executable of {@code checker}, relative to the configuration file. */
    public static Path installedExecutable(GenericCheckerConfig checker) {
        return DIRECTORY.resolve(checker.executable().getFileName());
    }

    /** Returns this table with {@code jar} as the JAR of {@code tool}. */
    public ToolsConfig with(ToolJar tool, Path jar) {
        var replaced = new EnumMap<ToolJar, Path>(ToolJar.class);
        replaced.putAll(jars);
        replaced.put(Objects.requireNonNull(tool, "tool"), Objects.requireNonNull(jar, "jar"));
        return new ToolsConfig(replaced, generic);
    }

    /** Returns these tools with {@code checker} as the generic checker. */
    public ToolsConfig withGeneric(GenericCheckerConfig checker) {
        return new ToolsConfig(jars, Optional.of(Objects.requireNonNull(checker, "checker")));
    }

    /** Returns the configured JAR of {@code tool}. */
    public Optional<Path> jar(ToolJar tool) {
        return Optional.ofNullable(jars.get(Objects.requireNonNull(tool, "tool")));
    }

    /** Resolves the JARs and the generic checker's executable relative to the directory of the configuration file. */
    ToolsConfig relativeTo(Path directory) {
        var resolved = new EnumMap<ToolJar, Path>(ToolJar.class);
        jars.forEach((tool, jar) -> resolved.put(tool, directory.resolve(jar).toAbsolutePath().normalize()));
        return new ToolsConfig(resolved, generic.map(checker -> checker.relativeTo(directory)));
    }
}
