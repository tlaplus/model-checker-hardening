package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.common.GeneratorAggregate;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import io.github.tlaplus.hardening.workflow.WorkflowRunSummary;
import io.github.tlaplus.hardening.workflow.execution.GeneratorSummary;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;

/** Formats live and final workflow counters with one stable table layout. */
final class RunTable {
    private RunTable() {}

    static String progress(WorkflowProgress progress) {
        return render(new View(
                "Workflow run in progress",
                progress.corpusEntries(),
                progress::backlog,
                progress.generator(),
                progress.stages(),
                progress.totalElapsed(),
                progress.phase().toString(),
                "run state"));
    }

    static String finished(Path corpus, WorkflowRunSummary summary) {
        return render(new View(
                "Workflow run finished for '" + corpus + "'",
                summary.corpus().totalEntries(),
                summary.corpus()::pendingEntries,
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
            ToLongFunction<CorpusStage> backlog,
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
                        view.backlog().applyAsLong(stage),
                        "awaiting " + stage.displayName());
            }
            printCounter(writer, view.generator().generated(), "generated inputs");
            printGenerationCounters(writer, view.generator().aggregate());
            printElapsed(writer, view.generator().elapsed(), "generator elapsed");
            for (var stage : CorpusStage.values()) {
                printVerdicts(writer, view.stages().get(stage), stage);
            }
            printElapsed(writer, view.totalElapsed(), "total elapsed");
            printStatistic(writer, view.stateValue(), view.stateLabel());
        }
        return output.toString();
    }

    private static void printVerdicts(
            PrintWriter writer, StageVerdictSummary summary, CorpusStage stage) {
        for (var verdict : stage.resultVerdicts()) {
            printCounter(
                    writer,
                    summary.count(verdict),
                    stage.displayName() + " " + verdict.countLabel());
        }
        printElapsed(writer, summary.elapsed(), stage.displayName() + " elapsed");
    }


    private static void printCounter(PrintWriter writer, long value, String label) {
        writer.printf("[%20d %-18s]%n", value, label);
    }

    private static void printGenerationCounters(PrintWriter writer, GeneratorAggregate generator) {
        var richness = generator.richness();
        printRichness(writer, richness.samples(), richness.minimum(), "min richness");
        printRichness(writer, richness.samples(), richness.maximum(), "max richness");
        printRichness(writer, richness.samples(), richness.average(), "avg richness");
        printCounter(writer, generator.attempts(), "candidate attempts");
        printCounter(writer, generator.rejected(), "generator rejected");
        printCounter(writer, generator.richnessRejected(), "richness rejected");
        printCounter(writer, generator.duplicates(), "duplicate inputs");
        printCounter(writer, generator.knownDefectRejections(), "known defects");
        generator.knownDefects().forEach((signature, count) -> printCounter(writer, count, "  " + signature));
    }

    private static void printRichness(PrintWriter writer, long samples, double value, String label) {
        printStatistic(writer, samples == 0 ? "n/a" : formatRichness(value), label);
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
