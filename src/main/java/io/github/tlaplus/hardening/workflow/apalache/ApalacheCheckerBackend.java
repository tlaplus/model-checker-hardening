package io.github.tlaplus.hardening.workflow.apalache;

import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.tool.ToolBackend;
import io.github.tlaplus.hardening.workflow.tool.ToolWorker;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Adapts persistent Apalache tool workers to the shared tool stage. */
public final class ApalacheCheckerBackend implements ToolBackend {
    private final CheckerStageConfig config;
    private final Path releaseJar;
    private final Path scratchDirectory;

    public ApalacheCheckerBackend(
            CheckerStageConfig config, Path releaseJar, Path scratchDirectory) {
        this.config = Objects.requireNonNull(config, "config");
        this.releaseJar = Objects.requireNonNull(releaseJar, "releaseJar");
        this.scratchDirectory = Objects.requireNonNull(scratchDirectory, "scratchDirectory");
    }

    @Override
    public CorpusStage stage() {
        return CorpusStage.APALACHE;
    }

    @Override
    public int workerCount() {
        return config.workers();
    }

    @Override
    public int cpuPermits() {
        return 1;
    }

    @Override
    public Function<TlaModule, String> renderer() {
        return ApalacheIrJson::render;
    }

    @Override
    public ToolWorker startWorker() throws WorkflowException, InterruptedException {
        return ApalacheProcess.start(releaseJar, scratchDirectory, config, config.timeout());
    }

    @Override
    public Optional<String> failureDetail(String diagnostic) {
        return ApalacheFailureDetail.extract(diagnostic);
    }
}
