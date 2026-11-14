package io.github.tlaplus.hardening.config;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A checker distribution that a corpus copies into its {@code tools/} directory, so its entries
 * keep being checked, and its libraries typed, with the release they were admitted under (ADR 0025
 * §3). The build stages each JAR beside {@code fuzztla.jar}.
 */
public enum ToolJar {
    /** The Apalache release: the Apalache checker and Snowcat typing of libraries and rules. */
    APALACHE("apalache"),
    /** TLC and SANY: the TLC checkers and the parser. */
    TLA2TOOLS("tla2tools");

    private final String name;

    ToolJar(String name) {
        this.name = name;
    }

    /** Returns the key of this tool in the {@code [tools]} table. */
    public String configKey() {
        return name;
    }

    /** Returns the file name of the staged JAR, and of its copy in a corpus. */
    public String fileName() {
        return name + ".jar";
    }

    /** Returns the tool whose {@code [tools]} key is {@code configKey}, if any. */
    public static Optional<ToolJar> fromConfigKey(String configKey) {
        Objects.requireNonNull(configKey, "configKey");
        return Arrays.stream(values()).filter(tool -> tool.name.equals(configKey)).findFirst();
    }

    /** Returns every {@code [tools]} key, in declaration order, for diagnostics. */
    public static List<String> configKeys() {
        return Arrays.stream(values()).map(ToolJar::configKey).toList();
    }
}
