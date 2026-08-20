package io.github.tlaplus.hardening.workflow.parser;

import io.github.tlaplus.hardening.config.ParserStageConfig;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.StageResult;
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
import java.util.EnumMap;
import java.util.Map;
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
    private final Map<CorpusStage, WorkQueue<Path>> checkerOutputs;
    private final Semaphore inputCapacity;
    private final WorkerGroup workers = new WorkerGroup("fuzztla-parser-");

    /**
     * @param checkerOutputs the queue each checker branch takes its work from, which must name
     *     every stage of {@link CorpusStage#checkerBranches()}
     */
    public ParserStage(
            ParserStageConfig config,
            int workerCount,
            long initialOccupancy,
            StageCounters counters,
            StageEnvironment environment,
            Path scratchDirectory,
            WorkQueue<Path> input,
            Map<CorpusStage, WorkQueue<Path>> checkerOutputs,
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
        var outputs = new EnumMap<CorpusStage, WorkQueue<Path>>(CorpusStage.class);
        outputs.putAll(Objects.requireNonNull(checkerOutputs, "checkerOutputs"));
        for (var checker : CorpusStage.checkerBranches()) {
            if (!outputs.containsKey(checker)) {
                throw new IllegalArgumentException("checkerOutputs is missing stage " + checker);
            }
        }
        this.checkerOutputs = Map.copyOf(outputs);
        this.inputCapacity = Objects.requireNonNull(inputCapacity, "inputCapacity");
        // A parser pass leaves the parser's result directories, so only failures occupy them.
        resultCapacity = new OccupancyGate(initialOccupancy, config.maximumEntries());
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
        checkerOutputs.values().forEach(WorkQueue::close);
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
            var destination = corpus.completeParser(
                    path,
                    new StageResult(
                            result.outcome().corpusVerdict(),
                            startTime,
                            StageResult.endedNow(startTime),
                            result.diagnostic()));
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

    /** Copies one parser pass into every checker branch, then queues each copy. */
    private void fanOut(Path parserPass) throws Exception {
        var corpus = environment.corpus();
        var inputName = parserPass.getFileName();
        corpus.fanOutParserPass(parserPass);
        for (var checker : CorpusStage.checkerBranches()) {
            checkerOutputs.get(checker).submit(
                    corpus.checkerInputPath(checker).resolve(inputName));
        }
    }

    private Duration timeout() {
        return Duration.ofSeconds(config.timeoutSeconds());
    }
}
