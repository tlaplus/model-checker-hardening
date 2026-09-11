package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.common.Diagnostics;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.WorkflowConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.StageScratchSet;
import io.github.tlaplus.hardening.signature.KnownDefectDatabase;
import io.github.tlaplus.hardening.workflow.aggregator.AggregatorStage;
import io.github.tlaplus.hardening.workflow.checker.CheckerRouting;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.OccupancyGate;
import io.github.tlaplus.hardening.workflow.execution.StageCounters;
import io.github.tlaplus.hardening.workflow.execution.StageEnvironment;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.execution.WorkflowMetrics;
import io.github.tlaplus.hardening.workflow.execution.WorkflowStage;
import io.github.tlaplus.hardening.workflow.input.GenerationPlan;
import io.github.tlaplus.hardening.workflow.input.InputAdmission;
import io.github.tlaplus.hardening.workflow.input.InputHandoff;
import io.github.tlaplus.hardening.workflow.input.KnownDefectQuarantine;
import io.github.tlaplus.hardening.workflow.input.PbtStage;
import io.github.tlaplus.hardening.workflow.parser.ParserBackend;
import io.github.tlaplus.hardening.workflow.parser.ParserRouting;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import io.github.tlaplus.hardening.workflow.tool.ToolStage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/**
 * The stages of one workflow invocation, wired to their queues, result capacities, and shared
 * collaborators, and the order in which they start and stop.
 */
final class StageGraph {
    /** What every invocation of one runner shares. */
    record Setup(FuzzTlaConfig config, SpecDecoders decoders, KnownDefectDatabase knownDefects) {
        Setup {
            Objects.requireNonNull(config, "config");
            Objects.requireNonNull(decoders, "decoders");
            Objects.requireNonNull(knownDefects, "knownDefects");
        }
    }

    /** The corpus one invocation runs on, and its arguments. */
    record Invocation(CorpusDirectory corpus, long seed, int maximumCpus, Path apalacheJar) {
        Invocation {
            Objects.requireNonNull(corpus, "corpus");
            Preconditions.requireNonnegative(seed, "seed");
            Preconditions.requirePositive(maximumCpus, "maximumCpus");
            Objects.requireNonNull(apalacheJar, "apalacheJar");
        }
    }

    /** What startup recovery found, and the statistics and scratch storage the stages use. */
    record Startup(CorpusInventory initial, WorkflowMetrics metrics, StageScratchSet scratch) {
        Startup {
            Objects.requireNonNull(initial, "initial");
            Objects.requireNonNull(metrics, "metrics");
            Objects.requireNonNull(scratch, "scratch");
        }
    }

    private final Map<CorpusStage, WorkQueue<Path>> queues = new EnumMap<>(CorpusStage.class);
    private final Map<CorpusStage, StageCounters> counters = new EnumMap<>(CorpusStage.class);
    private final Map<CorpusStage, ToolStage> checkers = new EnumMap<>(CorpusStage.class);
    private final WorkflowControl control;
    private final AggregatorStage aggregator;
    private final ToolStage parser;
    private final PbtStage inputs;

    StageGraph(Setup setup, Invocation invocation, Startup startup)
            throws IOException, CorpusException {
        var initial = startup.initial();
        var workflow = setup.config().workflow();
        for (var stage : CorpusStage.values()) {
            var queue = new WorkQueue<Path>();
            initial.pending(stage).forEach(queue::submit);
            queues.put(stage, queue);
            counters.put(
                    stage,
                    new StageCounters(
                            summary(initial, stage, startup.metrics()),
                            startup.metrics().clocks().of(stage)));
        }
        control = new WorkflowControl(queues.values().toArray(WorkQueue<?>[]::new));
        var environment = new StageEnvironment(
                invocation.corpus(),
                setup.decoders(),
                new CpuBudget(invocation.maximumCpus()),
                control);
        var inputCapacity = new Semaphore(
                workflow.inputs().maximumEntries()
                        - Math.toIntExact(initial.pendingEntries(CorpusStage.PARSER)),
                true);

        var checkerCapacities = new EnumMap<CorpusStage, OccupancyGate>(CorpusStage.class);
        for (var checker : CorpusStage.checkerBranches()) {
            checkerCapacities.put(checker, resultCapacity(workflow, initial, checker));
            checkers.put(
                    checker,
                    new ToolStage(
                            CheckerBackends.create(
                                    checker,
                                    workflow.checker(checker),
                                    new CheckerBackends.Resources(
                                            invocation.maximumCpus(),
                                            startup.scratch(),
                                            invocation.apalacheJar())),
                            new CheckerRouting(
                                    checkerCapacities.get(checker),
                                    queues.get(CorpusStage.AGGREGATOR)),
                            counters.get(checker),
                            environment,
                            queues.get(checker)));
        }
        aggregator = new AggregatorStage(
                initial.pendingEntries(CorpusStage.AGGREGATOR),
                counters.get(CorpusStage.AGGREGATOR),
                environment,
                queues.get(CorpusStage.AGGREGATOR),
                checkerCapacities);
        parser = new ToolStage(
                new ParserBackend(
                        workflow.parser(),
                        invocation.maximumCpus(),
                        startup.scratch().directory(CorpusStage.PARSER)),
                new ParserRouting(
                        resultCapacity(workflow, initial, CorpusStage.PARSER),
                        checkerQueues(),
                        inputCapacity),
                counters.get(CorpusStage.PARSER),
                environment,
                queues.get(CorpusStage.PARSER));
        inputs = new PbtStage(
                new InputAdmission(
                        setup.config().pbt(),
                        setup.knownDefects(),
                        KnownDefectQuarantine.open(
                                invocation.corpus(), workflow.inputs().knownDefectSamples())),
                new GenerationPlan(
                        setup.config().generatedKind(),
                        invocation.seed(),
                        workflow.maximumEntries(),
                        initial.totalEntries(),
                        invocation.maximumCpus()),
                environment,
                new InputHandoff(queues.get(CorpusStage.PARSER), inputCapacity),
                startup.metrics().generator());
    }

    WorkflowControl control() {
        return control;
    }

    /**
     * Runs every stage to completion and closes them. A stage failure is left in {@link #control()}
     * and reported by {@link #throwIfFailed()}.
     */
    void run() throws WorkflowException {
        try {
            // Drain recovered fan-in before checkers inspect their current result capacity.
            aggregator.start();
            aggregator.awaitRecovered();
            throwIfFailed();
            // Downstream stages start first so they can drain a recovered backlog immediately.
            for (var stage : checkers.values()) {
                stage.start();
            }
            parser.start();
            inputs.start();
            inputs.await();
            parser.await();
            for (var stage : checkers.values()) {
                stage.await();
            }
            queues.get(CorpusStage.AGGREGATOR).close();
            aggregator.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            control.fail(exception);
        } finally {
            stages().forEach(WorkflowStage::close);
        }
    }

    /** Rethrows the failure that stopped a stage, if any. */
    void throwIfFailed() throws WorkflowException {
        if (!control.hasFailed()) {
            return;
        }
        var failure = control.failure();
        if (failure instanceof WorkflowException workflowException) {
            throw workflowException;
        }
        throw new WorkflowException(
                "workflow stage failed: " + Diagnostics.message(failure), failure);
    }

    /** Returns what every stage has produced so far. */
    Map<CorpusStage, StageVerdictSummary> summaries() {
        var result = new EnumMap<CorpusStage, StageVerdictSummary>(CorpusStage.class);
        counters.forEach((stage, stageCounters) -> result.put(stage, stageCounters.summary()));
        return result;
    }

    /** Returns how many inputs currently wait in every stage's queue. */
    Map<CorpusStage, Long> backlog() {
        var result = new EnumMap<CorpusStage, Long>(CorpusStage.class);
        queues.forEach((stage, queue) -> result.put(stage, (long) queue.size()));
        return result;
    }

    /** Returns what one stage has produced according to an inventory of the corpus. */
    static StageVerdictSummary summary(
            CorpusInventory inventory, CorpusStage stage, WorkflowMetrics metrics) {
        return new StageVerdictSummary(
                inventory.counts(stage), metrics.clocks().of(stage).elapsed());
    }

    private List<WorkflowStage> stages() {
        var result = new ArrayList<WorkflowStage>();
        result.add(inputs);
        result.add(parser);
        result.addAll(checkers.values());
        result.add(aggregator);
        return result;
    }

    /** Returns the queue each checker branch takes its work from. */
    private Map<CorpusStage, WorkQueue<Path>> checkerQueues() {
        var result = new EnumMap<CorpusStage, WorkQueue<Path>>(CorpusStage.class);
        for (var checker : CorpusStage.checkerBranches()) {
            result.put(checker, queues.get(checker));
        }
        return result;
    }

    /** Returns the gate that bounds one stage's result directories, seeded from the corpus. */
    private static OccupancyGate resultCapacity(
            WorkflowConfig workflow, CorpusInventory initial, CorpusStage stage) {
        return new OccupancyGate(initial.resultEntries(stage), workflow.maximumEntries(stage));
    }
}
