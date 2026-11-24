package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.common.Role;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * The {@code [generic]} table (ADR 0033 §1): a model checker that claims compatibility with TLC or
 * Apalache, run as a native process for each input.
 *
 * @param name the tool's name in progress output, diagnostics and reports
 * @param compatibility the reference checker whose conventions the tool follows
 * @param executable the tool, relative to the configuration file until it is resolved
 * @param arguments the command-line arguments, each of which may contain placeholders
 */
@ArchRole(Role.CONFIG)
public record GenericCheckerConfig(
        String name, GenericCompatibility compatibility, Path executable, List<String> arguments) {
    /** The table a configuration without a generic checker shows, commented out: Manyan, against TLC. */
    static final GenericCheckerConfig EXAMPLE = new GenericCheckerConfig(
            "Manyan", GenericCompatibility.TLC, ToolsConfig.DIRECTORY.resolve("manyan-a9ca4f38028f"),
            List.of("-workers", "1", "-deadlock", "-config", GenericPlaceholder.CONFIG.token(),
                    GenericPlaceholder.MODULE.token()));

    public GenericCheckerConfig {
        Preconditions.require(!Objects.requireNonNull(name, "name").isBlank(), "generic.name must not be blank");
        Objects.requireNonNull(compatibility, "compatibility");
        Objects.requireNonNull(executable, "executable");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        for (var argument : arguments) {
            var unknown = GenericPlaceholder.unknownNames(argument);
            Preconditions.require(unknown.isEmpty(),
                    "generic.arguments: unknown placeholder {" + String.join("}, {", unknown) + "} in '" + argument + "'");
        }
    }

    /** Returns this checker with {@code replacement} as its executable. */
    public GenericCheckerConfig withExecutable(Path replacement) {
        return new GenericCheckerConfig(name, compatibility, replacement, arguments);
    }

    /** Resolves the executable relative to the directory of the configuration file. */
    GenericCheckerConfig relativeTo(Path directory) {
        return withExecutable(directory.resolve(executable).toAbsolutePath().normalize());
    }
}
