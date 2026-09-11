package io.github.tlaplus.hardening.workflow.parser;

import io.github.tlaplus.hardening.common.EnumMaps;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.StageResult;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.OccupancyGate;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.tool.StageRouting;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * Routes parser results. A pass is copied into every checker branch and leaves the parser's result
 * directories, so only a failure or a crash occupies parser capacity. Every recorded verdict frees
 * the {@code 00-inputs} slot the input stage reserved for the entry.
 */
public final class ParserRouting implements StageRouting {
    private final OccupancyGate resultCapacity;
    private final Map<CorpusStage, WorkQueue<Path>> checkerOutputs;
    private final Semaphore inputCapacity;

    /**
     * @param checkerOutputs the queue each checker branch takes its work from, which must name
     *     every stage of {@link CorpusStage#checkerBranches()}
     */
    public ParserRouting(
            OccupancyGate resultCapacity,
            Map<CorpusStage, WorkQueue<Path>> checkerOutputs,
            Semaphore inputCapacity) {
        this.resultCapacity = Objects.requireNonNull(resultCapacity, "resultCapacity");
        this.checkerOutputs = EnumMaps.requireKeys(
                CorpusStage.class, checkerOutputs, CorpusStage.checkerBranches(), "checkerOutputs");
        this.inputCapacity = Objects.requireNonNull(inputCapacity, "inputCapacity");
    }

    @Override
    public CpuBudget.Priority priority() {
        return CpuBudget.Priority.PARSER;
    }

    @Override
    public CorpusInput read(CorpusDirectory corpus, Path source)
            throws IOException, CorpusException {
        return corpus.readParserInput(source);
    }

    @Override
    public boolean reserveFor(CorpusVerdict verdict) {
        return verdict == CorpusVerdict.PASS || resultCapacity.reserve();
    }

    @Override
    public Path complete(CorpusDirectory corpus, Path source, StageResult result)
            throws IOException, CorpusException {
        return corpus.completeParser(source, result);
    }

    /** Frees the input slot and, for a pass, copies the entry into every checker branch. */
    @Override
    public void forward(CorpusDirectory corpus, Path destination, CorpusVerdict verdict)
            throws IOException, CorpusException {
        inputCapacity.release();
        if (verdict != CorpusVerdict.PASS) {
            return;
        }
        var inputName = destination.getFileName();
        corpus.fanOutParserPass(destination);
        for (var checker : CorpusStage.checkerBranches()) {
            checkerOutputs.get(checker).submit(
                    corpus.checkerInputPath(checker).resolve(inputName));
        }
    }

    @Override
    public void closeOutputs() {
        checkerOutputs.values().forEach(WorkQueue::close);
    }
}
