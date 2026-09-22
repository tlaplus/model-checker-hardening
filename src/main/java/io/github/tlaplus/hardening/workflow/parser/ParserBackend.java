package io.github.tlaplus.hardening.workflow.parser;

import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.config.ParserStageConfig;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.tool.ProcessToolWorker;
import io.github.tlaplus.hardening.workflow.tool.ToolBackend;
import io.github.tlaplus.hardening.workflow.tool.ToolWorker;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Runs SANY in persistent isolated JVMs, one per stage worker, each replaced after a crash. */
public final class ParserBackend implements ToolBackend {
    private final ParserStageConfig config;
    private final int workerCount;
    private final Path scratchDirectory;
    private final List<Path> classpath;

    /** {@code classpath} precedes SANY's own, so it can resolve instance-linked modules. */
    public ParserBackend(ParserStageConfig config, int workerCount, Path scratchDirectory, List<Path> classpath) {
        this.config = Objects.requireNonNull(config, "config");
        Preconditions.requirePositive(workerCount, "workerCount");
        this.workerCount = workerCount;
        this.scratchDirectory = Objects.requireNonNull(scratchDirectory, "scratchDirectory");
        this.classpath = List.copyOf(classpath);
    }

    @Override
    public CorpusStage stage() {
        return CorpusStage.PARSER;
    }

    @Override
    public int workerCount() {
        return workerCount;
    }

    @Override
    public int cpuPermits() {
        return 1;
    }

    @Override
    public ToolWorker startWorker() throws WorkflowException, InterruptedException {
        return new ProcessToolWorker(
                ParserProcess.start(scratchDirectory, classpath, config.timeout()), config.timeout());
    }
}
