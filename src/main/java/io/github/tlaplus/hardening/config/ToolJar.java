package io.github.tlaplus.hardening.config;

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
}
