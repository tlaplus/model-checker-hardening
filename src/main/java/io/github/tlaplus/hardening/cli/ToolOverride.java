package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.ToolJar;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One {@code --tool TOOL=FILE} of {@code fuzztla init}: the JAR a new corpus copies into {@code
 * tools/} in place of the staged one (ADR 0032 §2).
 *
 * @param tool the tool whose JAR is replaced
 * @param jar the JAR to copy
 */
record ToolOverride(ToolJar tool, Path jar) {
    ToolOverride {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(jar, "jar");
    }

    /** Returns the first tool that {@code overrides} name twice, if any. */
    static Optional<ToolJar> repeated(List<ToolOverride> overrides) {
        var seen = EnumSet.noneOf(ToolJar.class);
        return overrides.stream().map(ToolOverride::tool).filter(tool -> !seen.add(tool)).findFirst();
    }

    /** Returns {@code config} with the JAR of every override, absolute, in its {@code [tools]} table. */
    static FuzzTlaConfig apply(FuzzTlaConfig config, List<ToolOverride> overrides) {
        var tools = config.tools();
        for (var override : overrides) {
            tools = tools.with(override.tool(), override.jar().toAbsolutePath().normalize());
        }
        return config.withTools(tools);
    }
}
