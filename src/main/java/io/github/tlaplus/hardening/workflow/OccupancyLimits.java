package io.github.tlaplus.hardening.workflow;

import io.github.tlaplus.hardening.config.WorkflowConfig;
import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import java.util.Objects;

/** Checks an invocation and a recovered corpus against the configured capacity limits. */
final class OccupancyLimits {
    private final WorkflowConfig workflow;

    OccupancyLimits(WorkflowConfig workflow) {
        this.workflow = Objects.requireNonNull(workflow, "workflow");
    }

    /** Rejects a CPU limit this host cannot provide or a checker cannot run within. */
    void requireCpus(int maximumCpus) throws WorkflowException {
        var availableCpus = Runtime.getRuntime().availableProcessors();
        if (maximumCpus <= 0 || maximumCpus > availableCpus) {
            throw new IllegalArgumentException(
                    "maximumCpus must be in the range 1.." + availableCpus);
        }
        for (var checker : CorpusStage.checkerBranches()) {
            if (workflow.checker(checker).workers() > maximumCpus) {
                throw new WorkflowException(
                        "workflow."
                                + checker.metadataName()
                                + ".workers must not exceed run --max-cpus");
            }
        }
    }

    /** Rejects a corpus that already exceeds any configured limit. */
    void requireWithin(CorpusInventory inventory) throws WorkflowException {
        if (inventory.totalEntries() > workflow.maximumEntries()) {
            throw new WorkflowException(
                    "corpus contains more entries than workflow.max_entries");
        }
        if (inventory.pendingEntries(CorpusStage.PARSER) > workflow.inputs().maximumEntries()) {
            throw new WorkflowException("00-inputs exceeds workflow.inputs.max_entries");
        }
        for (var stage : CorpusStage.capacityLimitedStages()) {
            if (inventory.resultEntries(stage) > workflow.maximumEntries(stage)) {
                throw new WorkflowException(
                        stage.displayName()
                                + " result directories exceed workflow."
                                + stage.metadataName()
                                + ".max_entries");
            }
        }
    }

    /**
     * Reports whether the run can make no progress at all: a checker still has queued inputs it has
     * no capacity for, or the corpus is below its target but no input slot is available.
     */
    boolean exhausted(CorpusInventory initial) {
        var aggregationCanReleaseCapacity = initial.pendingEntries(CorpusStage.AGGREGATOR) > 0;
        for (var checker : CorpusStage.checkerBranches()) {
            if (initial.resultEntries(checker) >= workflow.maximumEntries(checker)
                    && initial.pendingEntries(checker) > 0
                    && !aggregationCanReleaseCapacity) {
                return true;
            }
        }
        return workflow.inputs().maximumEntries() == 0
                && initial.totalEntries() < workflow.maximumEntries();
    }
}
