package io.github.tlaplus.hardening.workflow.tool;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.corpus.StageResult;
import io.github.tlaplus.hardening.workflow.execution.StageCounters;
import io.github.tlaplus.hardening.workflow.execution.StageEnvironment;
import io.github.tlaplus.hardening.workflow.execution.StageJobLoop;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import io.github.tlaplus.hardening.workflow.execution.StageWorker;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkerGroup;
import io.github.tlaplus.hardening.workflow.execution.WorkflowStage;
import io.github.tlaplus.hardening.workflow.spec.GeneratedInputPreparation;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * One stage that runs an external tool over corpus entries: the parser, TLC, or Apalache.
 *
 * <p>Its workers claim queued entries under the shared CPU budget, regenerate and render each
 * input, run it through the backend's tool, and record the verdict. The {@link ToolBackend} says
 * which tool runs and how its processes live; the {@link StageRouting} says which entries the stage
 * owns, how it bounds its result directories, and where its results go.
 */
public final class ToolStage implements WorkflowStage {
    private final ToolBackend backend;
    private final StageRouting routing;
    private final StageEnvironment environment;
    private final StageCounters counters;
    private final StageJobLoop<Path> jobs;
    private final WorkQueue<Path> input;
    private final GeneratedInputPreparation inputPreparation;
    private final WorkerGroup workers;

    public ToolStage(
            ToolBackend backend,
            StageRouting routing,
            StageCounters counters,
            StageEnvironment environment,
            WorkQueue<Path> input) {
        this.backend = Objects.requireNonNull(backend, "backend");
        Preconditions.requirePositive(backend.workerCount(), "workerCount");
        Preconditions.requirePositive(backend.cpuPermits(), "cpuPermits");
        this.routing = Objects.requireNonNull(routing, "routing");
        this.counters = Objects.requireNonNull(counters, "counters");
        this.environment = Objects.requireNonNull(environment, "environment");
        this.input = Objects.requireNonNull(input, "input");
        var stage = backend.stage();
        inputPreparation = new GeneratedInputPreparation(
                stage.displayName(),
                environment.corpus(),
                environment.decoders(),
                backend.renderer());
        jobs = new StageJobLoop<>(
                input,
                environment.cpuBudget(),
                routing.priority(),
                backend.cpuPermits(),
                counters,
                environment.control());
        workers = new WorkerGroup("fuzztla-" + stage.metadataName() + "-");
    }

    @Override
    public String name() {
        return backend.stage().metadataName();
    }

    @Override
    public void start() {
        workers.start(backend.workerCount(), _ -> this::runWorker, routing::closeOutputs);
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
        routing.closeOutputs();
        workers.close();
    }

    private void runWorker() {
        StageWorker.run(
                environment.control(),
                backend.stage().displayName() + " worker",
                () -> {
                    try (var worker = new Worker()) {
                        jobs.run(worker::process);
                    }
                });
    }

    /**
     * One stage worker. A backend either starts a fresh child process per input or keeps one until
     * it crashes; either way this worker retires its tool after a crash verdict and lets the
     * backend supply a replacement for the next input.
     */
    private final class Worker implements AutoCloseable {
        private ToolWorker tool;

        private void process(Path path) throws Exception {
            if (!routing.reserveBeforeRun()) {
                environment.control().capacityReached();
                return;
            }
            var corpus = environment.corpus();
            var startTime = Instant.now();
            var source = inputPreparation.prepare(path, routing.read(corpus, path));
            if (tool == null) {
                tool = backend.startWorker();
            }
            var result = tool.check(source);
            if (result.outcome() == StageOutcome.CRASH) {
                tool.close();
                tool = null;
            }
            var verdict = result.outcome().corpusVerdict();
            if (!routing.reserveFor(verdict)) {
                environment.control().capacityReached();
                return;
            }
            var failure = result.failureCode()
                    .map(code -> new CheckerFailure(code, backend.failureDetail(result.diagnostic())));
            var destination = routing.complete(
                    corpus,
                    path,
                    new StageResult(
                            verdict,
                            startTime,
                            StageResult.endedNow(startTime),
                            failure,
                            result.diagnostic()));
            counters.record(verdict);
            routing.forward(corpus, destination, verdict);
        }

        @Override
        public void close() {
            if (tool != null) {
                tool.close();
            }
        }
    }
}
