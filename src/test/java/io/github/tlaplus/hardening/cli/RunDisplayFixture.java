package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.common.GeneratorAggregate;
import io.github.tlaplus.hardening.corpus.CorpusInventory;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.StageEntryCounts;
import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import io.github.tlaplus.hardening.workflow.WorkflowRunSummary;
import io.github.tlaplus.hardening.workflow.execution.GeneratorSummary;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.LongStream;
import org.jline.terminal.Size;
import org.jline.utils.AttributedString;

/** Shared realistic snapshots for layout, lifecycle, and change-tracking tests. */
final class RunDisplayFixture {
    private RunDisplayFixture() {}

    static WorkflowProgress sample() {
        return new WorkflowProgress(WorkflowProgress.Phase.RUNNING, 3,
                new GeneratorSummary(42, 1240, new GeneratorAggregate(1860, 300, 200, 80, 30,
                        Map.of("known", 10L), new GeneratorAggregate.Richness(1240, 0, 19, 4.5)),
                        Duration.ofSeconds(15)),
                Map.of(CorpusStage.PARSER, summary(1180, 0, 18, 2, 8),
                        CorpusStage.TLC, summary(1088, 45, 23, 2, 312),
                        CorpusStage.APALACHE, summary(1022, 45, 23, 1, 468),
                        CorpusStage.AGGREGATOR, summary(1060, 0, 28, 0, 1),
                        CorpusStage.QUALITY, summary(220, 0, 500, 0, 2)),
                Map.of(CorpusStage.PARSER, 40L, CorpusStage.TLC, 20L, CorpusStage.APALACHE, 86L,
                        CorpusStage.AGGREGATOR, 2L, CorpusStage.QUALITY, 0L),
                1240, Duration.ofSeconds(137));
    }

    private static StageVerdictSummary summary(long pass, long cex, long fail, long crash, long seconds) {
        return new StageVerdictSummary(new StageEntryCounts(Map.of(CorpusVerdict.PASS, pass,
                CorpusVerdict.COUNTEREXAMPLE, cex, CorpusVerdict.FAIL, fail, CorpusVerdict.CRASH, crash)),
                Duration.ofSeconds(seconds));
    }

    static WorkflowProgress empty() {
        var stages = new EnumMap<CorpusStage, StageVerdictSummary>(CorpusStage.class);
        var queues = new EnumMap<CorpusStage, Long>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            stages.put(stage, StageVerdictSummary.empty());
            queues.put(stage, 0L);
        }
        return new WorkflowProgress(WorkflowProgress.Phase.RUNNING, 0,
                new GeneratorSummary(42, 0, GeneratorAggregate.empty(), Duration.ZERO),
                stages, queues, 0, Duration.ZERO);
    }

    static WorkflowProgress extreme() {
        var stages = new EnumMap<CorpusStage, StageVerdictSummary>(CorpusStage.class);
        var queues = new EnumMap<CorpusStage, Long>(CorpusStage.class);
        var duration = Duration.ofSeconds(Long.MAX_VALUE);
        for (var stage : CorpusStage.values()) {
            var counts = new EnumMap<CorpusVerdict, Long>(CorpusVerdict.class);
            stage.resultVerdicts().forEach(verdict -> counts.put(verdict, 1_000_000_000_000_000_000L));
            stages.put(stage, new StageVerdictSummary(new StageEntryCounts(counts), duration));
            queues.put(stage, Long.MAX_VALUE);
        }
        var max = Long.MAX_VALUE;
        return new WorkflowProgress(WorkflowProgress.Phase.FINALIZING, Integer.MAX_VALUE,
                new GeneratorSummary(42, max, new GeneratorAggregate(max, max, max, max, max,
                        Map.of("known", max), new GeneratorAggregate.Richness(max, Double.MIN_VALUE,
                                Double.MAX_VALUE, Double.MAX_VALUE / 2)), duration),
                stages, queues, max, duration);
    }

    static WorkflowRunSummary finished(WorkflowProgress source) {
        var stages = new EnumMap<CorpusStage, CorpusInventory.StageEntries>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            // Once workers stop, claimed checker inputs return to the pending inventory.
            var pendingCount = CorpusStage.checkerBranches().contains(stage)
                    ? source.stage(CorpusStage.PARSER).count(CorpusVerdict.PASS) - source.stage(stage).processed()
                    : source.backlog(stage);
            var pending = LongStream.range(0, pendingCount)
                    .mapToObj(index -> Path.of(stage.metadataName(), Long.toString(index))).toList();
            stages.put(stage, new CorpusInventory.StageEntries(pending, source.stage(stage).counts(),
                    source.stage(stage).processed()));
        }
        return new WorkflowRunSummary(WorkflowRunSummary.StopReason.COMPLETED, source.generator(),
                source.stages(), new CorpusInventory(stages, new TreeMap<>()), source.totalElapsed());
    }

    static Map<RunMetric, RunValue> values(WorkflowProgress source) {
        return RunView.live(source).metrics();
    }

    static List<AttributedString> render(WorkflowProgress source, Size size) {
        return ProgressLayout.render(new RunText(values(source), Precision.COMPACT, RunPalette.PLAIN, Set.of()),
                size, true);
    }

    static String plain(List<AttributedString> lines) {
        return String.join("\n", lines.stream().map(AttributedString::toString).toList());
    }
}
