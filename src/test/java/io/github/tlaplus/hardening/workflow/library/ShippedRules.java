package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.workflow.StagedTools;
import io.github.tlaplus.hardening.config.ToolJar;
import io.github.tlaplus.hardening.config.CheckerProfile;
import io.github.tlaplus.hardening.config.RuleLibraryConfig;
import io.github.tlaplus.hardening.gen.rewrite.RewriteLibrary;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.nio.file.Path;
import java.util.List;

/** The rule module the repository ships in libraries/rewrites (ADR 0017), as tests configure it. */
public final class ShippedRules {
    private ShippedRules() {}

    /** The shipped rule module at its annotated weights. */
    public static RuleLibraryConfig config() {
        return RuleLibraryConfig.of(
                new RuleLibraryConfig.RuleModule("Rewrites", List.of(Path.of("libraries/rewrites").toAbsolutePath())));
    }

    /** The shipped rules, type-checked with Apalache's default settings. */
    public static RewriteLibrary library() throws WorkflowException {
        return RuleLibraryPreparation.prepare(config(), CheckerProfile.APALACHE.defaults(), StagedTools.locate(ToolJar.APALACHE)).library();
    }
}
