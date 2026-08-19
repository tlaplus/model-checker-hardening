package io.github.tlaplus.hardening.workflow.parser;

import io.github.tlaplus.hardening.config.ParserStageConfig;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.OccupancyGate;
import io.github.tlaplus.hardening.workflow.execution.StageCounters;
import io.github.tlaplus.hardening.workflow.execution.StageEnvironment;
import io.github.tlaplus.hardening.workflow.execution.StageJobLoop;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import io.github.tlaplus.hardening.workflow.execution.StageWorker;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkerGroup;
import io.github.tlaplus.hardening.workflow.execution.WorkflowStage;
import io.github.tlaplus.hardening.workflow.spec.ExprInputToSpec;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/** Concurrent parser stage backed by persistent isolated SANY JVMs. */
public final class ParserStage implements WorkflowStage {
    private final ParserStageConfig config;
    private final int workerCount;
    private final StageEnvironment environment;
    private final StageCounters counters;
    private final OccupancyGate resultCapacity;
    private final StageJobLoop<Path> jobs;
    private final Path scratchDirectory;
    private final WorkQueue<Path> input;
    private final List<WorkQueue<Path>> checkerOutputs;
    private final Semaphore inputCapacity;
    private final WorkerGroup workers = new WorkerGroup("fuzztla-parser-");

    public ParserStage(
            ParserStageConfig config,
            int workerCount,
            StageVerdictSummary initialStatistics,
            StageCounters counters,
            StageEnvironment environment,
            Path scratchDirectory,
            WorkQueue<Path> input,
            WorkQueue<Path> tlcOutput,
            WorkQueue<Path> apalacheOutput,
            Semaphore inputCapacity) {
        this.config = Objects.requireNonNull(config, "config");
        if (workerCount <= 0) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        this.workerCount = workerCount;
        this.environment = Objects.requireNonNull(environment, "environment");
        this.counters = Objects.requireNonNull(counters, "counters");
        this.scratchDirectory = Objects.requireNonNull(scratchDirectory, "scratchDirectory");
        this.input = Objects.requireNonNull(input, "input");
        checkerOutputs = List.of(
                Objects.requireNonNull(tlcOutput, "tlcOutput"),
                Objects.requireNonNull(apalacheOutput, "apalacheOutput"));
        this.inputCapacity = Objects.requireNonNull(inputCapacity, "inputCapacity");
        Objects.requireNonNull(initialStatistics, "initialStatistics");
        // A parser pass leaves the parser's result directories, so only failures occupy them.
        resultCapacity = new OccupancyGate(
                initialStatistics.failed() + initialStatistics.crashed(), config.maximumEntries());
        jobs = new StageJobLoop<>(
                input,
                environment.cpuBudget(),
                CpuBudget.Priority.PARSER,
                1,
                counters,
                environment.control());
    }

    @Override
    public String name() {
        return "parser";
    }

    @Override
    public void start() {
        workers.start(workerCount, _ -> this::runWorker, this::closeCheckerOutputs);
    }

    @Override
    public void await() throws InterruptedException {
        workers.await();
    }

    public StageVerdictSummary summary() {
        return counters.summary();
    }

    @Override
    public void close() {
        input.close();
        closeCheckerOutputs();
        workers.close();
    }

    private void closeCheckerOutputs() {
        checkerOutputs.forEach(WorkQueue::close);
    }

    private void runWorker() {
        StageWorker.run(environment.control(), "parser worker", () -> {
            try (var worker = new Worker()) {
                jobs.run(worker::parse);
            }
        });
    }

    /** One parser worker and the persistent SANY JVM it reuses until that JVM dies. */
    private final class Worker implements AutoCloseable {
        private ParserProcess process;

        private void parse(Path path) throws Exception {
            var corpus = environment.corpus();
            var startTime = Instant.now();
            var payload = corpus.readExpressionInput(path);
            var source = ExprInputToSpec.render(
                    "parser", path, payload, corpus, environment.generator());
            if (process == null) {
                process = ParserProcess.start(scratchDirectory, timeout());
            }
            var result = process.parse(source, timeout());
            if (result.outcome() == StageOutcome.CRASH) {
                // A crash verdict has already closed the child process.
                process = null;
            }
            if (result.outcome() != StageOutcome.PASS && !resultCapacity.reserve()) {
                environment.control().capacityReached();
                return;
            }
            var endTime = Instant.now();
            if (endTime.isBefore(startTime)) {
                endTime = startTime;
            }
            var destination = corpus.completeParser(
                    path,
                    result.outcome().corpusVerdict(),
                    startTime,
                    endTime,
                    result.diagnostic());
            inputCapacity.release();
            counters.record(result.outcome().corpusVerdict());
            if (result.outcome() == StageOutcome.PASS) {
                fanOut(destination);
            }
        }

        @Override
        public void close() {
            if (process != null) {
                process.close();
            }
        }
    }

    /** Copies one parser pass into both checker branches, then queues both copies. */
    private void fanOut(Path parserPass) throws Exception {
        var corpus = environment.corpus();
        var inputName = parserPass.getFileName();
        corpus.fanOutParserPass(parserPass);
        checkerOutputs.get(0).submit(corpus.resolve(CorpusPath.TLC_INPUT).resolve(inputName));
        checkerOutputs.get(1).submit(corpus.resolve(CorpusPath.APALACHE_INPUT).resolve(inputName));
    }

    private Duration timeout() {
        return Duration.ofSeconds(config.timeoutSeconds());
    }
}
