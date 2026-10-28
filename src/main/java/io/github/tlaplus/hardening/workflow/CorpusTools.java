package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.ToolJar;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The tool JARs a corpus runs (ADR 0025 §3): those of its {@code [tools]} table, or the ones staged
 * with FuzzTLA for a tool the table leaves out.
 */
public final class CorpusTools {
    private CorpusTools() {}

    /** Refuses a configured JAR that does not exist, before any worker starts. */
    public static void require(FuzzTlaConfig config) throws WorkflowException {
        for (var tool : ToolJar.values()) {
            var jar = config.tools().jar(tool);
            if (jar.isPresent() && !Files.isRegularFile(jar.get())) {
                throw new WorkflowException("tools." + tool.configKey() + " does not exist: " + jar.get());
            }
        }
    }

    /** Returns the Apalache JAR that checks and types libraries and rules. */
    public static Path apalache(FuzzTlaConfig config) throws WorkflowException {
        var configured = Objects.requireNonNull(config, "config").tools().jar(ToolJar.APALACHE);
        return configured.isPresent() ? configured.get() : StagedTools.locate(ToolJar.APALACHE);
    }

    /**
     * Returns what goes in front of the class path of the workers that read TLA+ source, the parser
     * and TLC: the library classpath they resolve linked modules and TLC overrides from, as before,
     * then the corpus's {@code tla2tools} JAR, so its TLC wins over FuzzTLA's own copy.
     */
    public static List<Path> sourceCheckerClasspath(FuzzTlaConfig config) {
        var classpath = new ArrayList<>(Objects.requireNonNull(config, "config").libraries().sourceCheckerClasspath());
        config.tools().jar(ToolJar.TLA2TOOLS).ifPresent(classpath::add);
        return List.copyOf(classpath);
    }
}
