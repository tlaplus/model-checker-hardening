package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import io.github.tlaplus.hardening.workflow.WorkflowRunSummary;
import io.github.tlaplus.hardening.workflow.execution.GeneratorSummary;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/** Formats live and final workflow counters with one stable table layout. */
final class RunTable {
    private RunTable() {}

    static String progress(WorkflowProgress progress) {
        var backlog = new EnumMap<CorpusStage, Long>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            backlog.put(stage, progress.backlog(stage));
        }
        return render(new View(
                "Workflow run in progress",
                progress.corpusEntries(),
                backlog,
                progress.generator(),
                progress.stages(),
                progress.totalElapsed(),
                progress.phase().toString(),
                "run state"));
    }

    static String finished(Path corpus, WorkflowRunSummary summary) {
        var backlog = new EnumMap<CorpusStage, Long>(CorpusStage.class);
        for (var stage : CorpusStage.values()) {
            backlog.put(stage, summary.corpus().pendingEntries(stage));
        }
        return render(new View(
                "Workflow run finished for '" + corpus + "'",
                summary.corpus().totalEntries(),
                backlog,
                summary.generator(),
                summary.stages(),
                summary.totalElapsed(),
                summary.stopReason().toString(),
                "stop reason"));
    }

    /** Everything the table shows, gathered from either a live snapshot or a final summary. */
    private record View(
            String header,
            long corpusEntries,
            Map<CorpusStage, Long> backlog,
            GeneratorSummary generator,
            Map<CorpusStage, StageVerdictSummary> stages,
            Duration totalElapsed,
            String stateValue,
            String stateLabel) {}

    private static String render(View view) {
        var output = new StringWriter();
        try (var writer = new PrintWriter(output)) {
            writer.printf("%s%n%n", view.header());
            printCounter(writer, view.corpusEntries(), "corpus entries");
            for (var stage : CorpusStage.values()) {
                printCounter(
                        writer,
                        view.backlog().get(stage),
                        "awaiting " + stage.displayName());
            }
            printCounter(writer, view.generator().generated(), "generated inputs");
            printGenerationCounters(writer, view.generator());
            printElapsed(writer, view.generator().elapsed(), "generator elapsed");
            for (var stage : CorpusStage.values()) {
                printVerdicts(writer, view.stages().get(stage), stage.displayName());
            }
            printElapsed(writer, view.totalElapsed(), "total elapsed");
            writer.printf("[%20s %-18s]%n", view.stateValue(), view.stateLabel());
        }
        return output.toString();
    }

    private static void printVerdicts(
            PrintWriter writer, StageVerdictSummary stage, String label) {
        printCounter(writer, stage.passed(), label + " passed");
        printCounter(writer, stage.failed(), label + " failed");
        printCounter(writer, stage.crashed(), label + " crashed");
        printElapsed(writer, stage.elapsed(), label + " elapsed");
    }

    private static void printCounter(PrintWriter writer, long value, String label) {
        writer.printf("[%20d %-18s]%n", value, label);
    }

    private static void printGenerationCounters(PrintWriter writer, GeneratorSummary generator) {
        if (generator.richnessSamples() == 0) {
            printStatistic(writer, "n/a", "min richness");
            printStatistic(writer, "n/a", "max richness");
            printStatistic(writer, "n/a", "avg richness");
        } else {
            printStatistic(
                    writer, formatRichness(generator.minimumRichness()), "min richness");
            printStatistic(
                    writer, formatRichness(generator.maximumRichness()), "max richness");
            printStatistic(
                    writer, formatRichness(generator.averageRichness()), "avg richness");
        }
        printCounter(writer, generator.attempts(), "candidate attempts");
        printCounter(writer, generator.rejected(), "generator rejected");
        printCounter(writer, generator.richnessRejected(), "richness rejected");
        printCounter(writer, generator.duplicates(), "duplicate inputs");
    }

    private static void printStatistic(PrintWriter writer, String value, String label) {
        writer.printf("[%20s %-18s]%n", value, label);
    }

    private static void printElapsed(PrintWriter writer, Duration elapsed, String label) {
        printStatistic(writer, HumanDuration.format(elapsed), label);
    }

    private static String formatRichness(double value) {
        if (value == 0.0) {
            return "0";
        }
        if (value < 0.001 || value >= 1_000_000_000_000.0) {
            return String.format(Locale.ROOT, "%.3e", value);
        }
        var formatted = String.format(Locale.ROOT, "%.3f", value);
        var end = formatted.length();
        while (end > 0 && formatted.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && formatted.charAt(end - 1) == '.') {
            end--;
        }
        return formatted.substring(0, end);
    }
}
