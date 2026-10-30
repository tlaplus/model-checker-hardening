package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.config.ToolJar;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Locates the tool JARs the build stages beside FuzzTLA's own JAR, or beside its classes when it
 * runs from the build directory. {@code bin/fuzztla} snapshots them together with {@code
 * fuzztla.jar}.
 */
public final class StagedTools {
    private StagedTools() {}

    /** Returns the staged JAR of {@code tool}, which must exist. */
    public static Path locate(ToolJar tool) throws WorkflowException {
        Objects.requireNonNull(tool, "tool");
        try {
            var source = StagedTools.class.getProtectionDomain().getCodeSource();
            if (source == null) {
                throw new WorkflowException("cannot locate the FuzzTLA code source");
            }
            var directory = Path.of(source.getLocation().toURI()).toAbsolutePath().normalize().getParent();
            if (directory == null) {
                throw new WorkflowException("cannot locate the FuzzTLA build directory");
            }
            var candidate = directory.resolve(tool.fileName());
            if (!Files.isRegularFile(candidate)) {
                throw new WorkflowException("staged " + tool.configKey() + " JAR does not exist: " + candidate);
            }
            return candidate;
        } catch (URISyntaxException exception) {
            throw new WorkflowException("cannot locate the staged " + tool.configKey() + " JAR", exception);
        }
    }
}
