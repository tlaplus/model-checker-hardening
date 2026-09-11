package io.github.tlaplus.hardening.workflow.checker;

import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.StageResult;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.OccupancyGate;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.tool.StageRouting;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Routes a model checker's results. Every verdict stays in the checker's result directories until
 * aggregation, so capacity is reserved before the checker runs, and each completed result notifies
 * the aggregator. The aggregator queue is shared by every checker, so this routing never closes it.
 */
public final class CheckerRouting implements StageRouting {
    private final OccupancyGate resultCapacity;
    private final WorkQueue<Path> aggregator;

    public CheckerRouting(OccupancyGate resultCapacity, WorkQueue<Path> aggregator) {
        this.resultCapacity = Objects.requireNonNull(resultCapacity, "resultCapacity");
        this.aggregator = Objects.requireNonNull(aggregator, "aggregator");
    }

    @Override
    public CpuBudget.Priority priority() {
        return CpuBudget.Priority.CHECKER;
    }

    @Override
    public CorpusInput read(CorpusDirectory corpus, Path source)
            throws IOException, CorpusException {
        return corpus.readCheckerInput(source);
    }

    @Override
    public boolean reserveBeforeRun() {
        return resultCapacity.reserve();
    }

    @Override
    public Path complete(CorpusDirectory corpus, Path source, StageResult result)
            throws IOException, CorpusException {
        return corpus.completeChecker(source, result);
    }

    @Override
    public void forward(CorpusDirectory corpus, Path destination, CorpusVerdict verdict) {
        aggregator.submit(destination);
    }
}
