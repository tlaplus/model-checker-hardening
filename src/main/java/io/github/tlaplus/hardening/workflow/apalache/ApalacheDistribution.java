package io.github.tlaplus.hardening.workflow.apalache;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Locates the pinned Apalache release JAR staged beside FuzzTLA's build output. */
public final class ApalacheDistribution {
    public static final String JAR_NAME = "apalache.jar";

    private ApalacheDistribution() {}

    public static Path locate() throws WorkflowException {
        try {
            var source = ApalacheDistribution.class
                    .getProtectionDomain()
                    .getCodeSource();
            if (source == null) {
                throw new WorkflowException("cannot locate the FuzzTLA code source");
            }
            var application = Path.of(source.getLocation().toURI())
                    .toAbsolutePath()
                    .normalize();
            var directory = application.getParent();
            if (directory == null) {
                throw new WorkflowException("cannot locate the FuzzTLA build directory");
            }
            var candidate = directory.resolve(JAR_NAME);
            if (!Files.isRegularFile(candidate)) {
                throw new WorkflowException(
                        "Apalache release JAR does not exist: " + candidate);
            }
            return candidate;
        } catch (URISyntaxException exception) {
            throw new WorkflowException("cannot locate the Apalache release JAR", exception);
        }
    }
}
