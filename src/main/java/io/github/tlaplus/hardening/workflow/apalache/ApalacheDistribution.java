package io.github.tlaplus.hardening.workflow.apalache;

import io.github.tlaplus.hardening.config.ToolJar;
import io.github.tlaplus.hardening.workflow.StagedTools;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.nio.file.Path;

/** Locates the pinned Apalache release JAR staged beside FuzzTLA's build output. */
public final class ApalacheDistribution {
    private ApalacheDistribution() {}

    public static Path locate() throws WorkflowException {
        return StagedTools.locate(ToolJar.APALACHE);
    }
}
