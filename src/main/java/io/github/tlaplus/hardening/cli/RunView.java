package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import io.github.tlaplus.hardening.workflow.WorkflowRunSummary;
import io.github.tlaplus.hardening.workflow.execution.GeneratorSummary;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The statistics shared by live and final reports, without layout or terminal state. The status is
 * the live phase or the final stop reason.
 */
record RunView(Enum<?> status, int generation, long corpusEntries, GeneratorSummary generator,
        Map<CorpusStage, StageVerdictSummary> stages, Map<CorpusStage, Long> backlog,
        Duration elapsed) {

    static RunView live(WorkflowProgress progress) {
        return new RunView(progress.phase(), progress.generation(), progress.corpusEntries(),
                progress.generator(), progress.stages(), progress.backlog(), progress.totalElapsed());
    }

    static RunView finished(WorkflowRunSummary summary) {
        var backlog = new EnumMap<CorpusStage, Long>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            backlog.put(stage, summary.corpus().pendingEntries(stage));
        }
        return new RunView(summary.stopReason(), summary.corpus().latestGeneration(),
                summary.corpus().totalEntries(), summary.generator(), summary.stages(), backlog,
                summary.totalElapsed());
    }

    /** Every value a report may show, keyed by its stable identity. */
    Map<RunMetric, RunValue> metrics() {
        var result = new HashMap<RunMetric, RunValue>();
        result.put(RunMetric.State.STATUS, new RunValue.Status(status));
        for (var field : RunMetric.Field.values()) {
            result.put(field, field.read(this));
        }
        for (var stage : CorpusStage.values()) {
            var summary = stages.get(stage);
            result.put(new RunMetric.Queue(stage), new RunValue.Count(backlog.get(stage)));
            result.put(new RunMetric.StageElapsed(stage), new RunValue.Elapsed(summary.elapsed()));
            for (var verdict : stage.resultVerdicts()) {
                result.put(new RunMetric.Result(stage, verdict), new RunValue.Count(summary.count(verdict)));
            }
        }
        return Map.copyOf(result);
    }
}
