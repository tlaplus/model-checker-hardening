package io.github.tlaplus.hardening.workflow.tlc;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.tool.ToolBackend;
import io.github.tlaplus.hardening.workflow.tool.ToolWorker;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Adapts fresh isolated TLC invocations to the shared tool stage. */
public final class TlcCheckerBackend implements ToolBackend {
    private final CheckerStageConfig config;
    private final int workerCount;
    private final Path scratchDirectory;
    private final List<Path> classpath;

    /** {@code classpath} precedes TLC's own, so it can supply modules and their Java overrides. */
    public TlcCheckerBackend(
            CheckerStageConfig config, int workerCount, Path scratchDirectory, List<Path> classpath) {
        this.config = Objects.requireNonNull(config, "config");
        Preconditions.requirePositive(workerCount, "workerCount");
        this.workerCount = workerCount;
        this.scratchDirectory = Objects.requireNonNull(scratchDirectory, "scratchDirectory");
        this.classpath = List.copyOf(classpath);
    }

    @Override
    public CorpusStage stage() {
        return CorpusStage.TLC;
    }

    @Override
    public int workerCount() {
        return workerCount;
    }

    @Override
    public int cpuPermits() {
        return config.workers();
    }

    @Override
    public ToolWorker startWorker() {
        return source -> TlcProcess.check(scratchDirectory, classpath, source, config, config.timeout());
    }

    @Override
    public Optional<String> failureDetail(String diagnostic) {
        return TlcFailureDetail.extract(diagnostic);
    }
}
