package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.GenericCheckerConfig;
import io.github.tlaplus.hardening.config.SeedConfig;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.StageScratchSet;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheCheckerBackend;
import io.github.tlaplus.hardening.workflow.generic.GenericCheckerBackend;
import io.github.tlaplus.hardening.workflow.tlc.TlcCheckerBackend;
import io.github.tlaplus.hardening.workflow.tlc.TlcSettings;
import io.github.tlaplus.hardening.workflow.tool.ToolBackend;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * How each model-checker stage builds its backend for one invocation. Adding a checker adds one
 * entry here; loading this class fails if a checker branch has none.
 */
@ArchRole(Role.POLICY)
@Key
final class CheckerBackends {
    /** What one invocation provides to the backends it builds. */
    record Resources(int maximumCpus, StageScratchSet scratch, Tools tools, SeedConfig seeds) {
        Resources {
            Preconditions.requirePositive(maximumCpus, "maximumCpus");
            Objects.requireNonNull(seeds, "seeds");
            Objects.requireNonNull(scratch, "scratch");
            Objects.requireNonNull(tools, "tools");
        }
    }

    /**
     * The tools the checkers run. {@code sourceClasspath} precedes the class path of checkers that
     * read TLA+ source; {@code generic} is the corpus's generic checker, if it has one (ADR 0033).
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    record Tools(Path apalacheJar, List<Path> sourceClasspath, Optional<GenericCheckerConfig> generic) {
        Tools {
            Objects.requireNonNull(apalacheJar, "apalacheJar");
            sourceClasspath = List.copyOf(sourceClasspath);
            Objects.requireNonNull(generic, "generic");
        }

        /** Returns the tools of {@code config}, with its Apalache JAR already located. */
        static Tools of(FuzzTlaConfig config, Path apalacheJar) {
            return new Tools(apalacheJar, CorpusTools.sourceCheckerClasspath(config), config.tools().generic());
        }
    }

    @FunctionalInterface
    private interface Factory {
        ToolBackend create(CorpusStage stage, CheckerStageConfig config, Resources resources);
    }

    private static final Map<CorpusStage, Factory> FACTORIES = Map.of(
            CorpusStage.TLC, CheckerBackends::tlc,
            CorpusStage.TLCN, CheckerBackends::tlc,
            CorpusStage.APALACHE,
            (stage, config, resources) -> new ApalacheCheckerBackend(
                    config,
                    resources.tools().apalacheJar(),
                    resources.scratch().directory(stage)),
            CorpusStage.GENERIC,
            (stage, config, resources) -> new GenericCheckerBackend(
                    stage,
                    config,
                    resources.tools().generic().orElseThrow(
                            () -> new IllegalStateException("generic runs without a [generic] table")),
                    resources.scratch().directory(stage)));

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
        return factory.create(stage, Objects.requireNonNull(config, "config"), resources);
    }

    /** Every TLC stage runs one fresh TLC JVM per input, with the stage's own number of workers. */
    private static ToolBackend tlc(CorpusStage stage, CheckerStageConfig config, Resources resources) {
        return new TlcCheckerBackend(
                stage,
                new TlcSettings(config, resources.seeds()),
                resources.maximumCpus() / config.workers(),
                resources.scratch().directory(stage),
                resources.tools().sourceClasspath());
    }
}
