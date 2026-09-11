package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.StageScratchSet;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheCheckerBackend;
import io.github.tlaplus.hardening.workflow.tlc.TlcCheckerBackend;
import io.github.tlaplus.hardening.workflow.tool.ToolBackend;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * How each model-checker stage builds its backend for one invocation. Adding a checker adds one
 * entry here; loading this class fails if a checker branch has none.
 */
final class CheckerBackends {
    /** What one invocation provides to the backends it builds. */
    record Resources(int maximumCpus, StageScratchSet scratch, Path apalacheJar) {
        Resources {
            Preconditions.requirePositive(maximumCpus, "maximumCpus");
            Objects.requireNonNull(scratch, "scratch");
            Objects.requireNonNull(apalacheJar, "apalacheJar");
        }
    }

    @FunctionalInterface
    private interface Factory {
        ToolBackend create(CheckerStageConfig config, Resources resources);
    }

    private static final Map<CorpusStage, Factory> FACTORIES = Map.of(
            CorpusStage.TLC,
            (config, resources) -> new TlcCheckerBackend(
                    config,
                    resources.maximumCpus() / config.workers(),
                    resources.scratch().directory(CorpusStage.TLC)),
            CorpusStage.APALACHE,
            (config, resources) -> new ApalacheCheckerBackend(
                    config,
                    resources.apalacheJar(),
                    resources.scratch().directory(CorpusStage.APALACHE)));

    static {
        if (!FACTORIES.keySet().equals(Set.copyOf(CorpusStage.checkerBranches()))) {
            throw new ExceptionInInitializerError(
                    "every checker branch needs exactly one backend factory");
        }
    }

    private CheckerBackends() {}

    /** Returns the backend of one checker stage, configured for this invocation. */
    static ToolBackend create(CorpusStage stage, CheckerStageConfig config, Resources resources) {
        var factory = FACTORIES.get(Objects.requireNonNull(stage, "stage"));
        Preconditions.require(factory != null, stage + " is not a checker stage");
        return factory.create(Objects.requireNonNull(config, "config"), resources);
    }
}
