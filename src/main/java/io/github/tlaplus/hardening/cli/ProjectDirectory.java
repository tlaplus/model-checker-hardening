package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import java.nio.file.Path;
import java.util.Optional;

/** Locates the FuzzTLA project directory that ships the signature databases and scripts. */
final class ProjectDirectory {
    /** The system property through which {@code bin/fuzztla} names the project directory. */
    static final String HOME_PROPERTY = "fuzztla.home";

    private ProjectDirectory() {}

    /**
     * Returns the project directory. {@code bin/fuzztla} runs a snapshot of the JAR from a
     * temporary directory, so it names the project in {@link #HOME_PROPERTY}. Without the launcher,
     * the JAR runs in place from the project's {@code target} directory, beside the staged
     * Apalache JAR.
     */
    static Optional<Path> locate() {
        var home = System.getProperty(HOME_PROPERTY);
        if (home != null) {
            return Optional.of(Path.of(home).toAbsolutePath().normalize());
        }
        try {
            return Optional.ofNullable(ApalacheDistribution.locate().getParent()).map(Path::getParent);
        } catch (WorkflowException exception) {
            return Optional.empty();
        }
    }
}
