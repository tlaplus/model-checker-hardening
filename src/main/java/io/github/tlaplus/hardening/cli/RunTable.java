package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import io.github.tlaplus.hardening.workflow.WorkflowRunSummary;
import io.github.tlaplus.hardening.workflow.input.PbtStageSummary;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Locale;

/** Formats live and final workflow counters with one stable table layout. */
final class RunTable {
    private RunTable() {}

    static String progress(WorkflowProgress progress) {
        var output = new StringWriter();
        try (var writer = new PrintWriter(output)) {
            writer.printf("Workflow run in progress%n%n");
            printCounter(writer, progress.corpusEntries(), "corpus entries");
            printCounter(writer, progress.awaitingParser(), "awaiting parser");
            printCounter(writer, progress.awaitingTlc(), "awaiting TLC");
            printCounter(writer, progress.pendingApalache(), "pending Apalache");
            printCounter(writer, progress.generator().added(), "generated inputs");
            printGenerationCounters(writer, progress.generator());
            printCounter(writer, progress.parser().passed(), "parser passed");
            printCounter(writer, progress.parser().failed(), "parser failed");
            printCounter(writer, progress.parser().crashed(), "parser crashed");
            printCounter(writer, progress.tlc().passed(), "TLC passed");
            printCounter(writer, progress.tlc().failed(), "TLC failed");
            printCounter(writer, progress.tlc().crashed(), "TLC crashed");
            writer.printf("[%20s %-18s]%n", progress.phase(), "run state");
        }
        return output.toString();
    }

    static String finished(Path corpus, WorkflowRunSummary summary) {
        var output = new StringWriter();
        try (var writer = new PrintWriter(output)) {
            writer.printf("Workflow run finished for '%s'%n%n", corpus);
            printCounter(writer, summary.corpus().totalEntries(), "corpus entries");
            printCounter(writer, summary.corpus().inputEntries(), "awaiting parser");
            printCounter(writer, summary.corpus().tlcInputEntries(), "awaiting TLC");
            printCounter(writer, summary.corpus().apalacheInputEntries(), "pending Apalache");
            printCounter(writer, summary.generator().added(), "generated inputs");
            printGenerationCounters(writer, summary.generator());
            printCounter(writer, summary.parser().passed(), "parser passed");
            printCounter(writer, summary.parser().failed(), "parser failed");
            printCounter(writer, summary.parser().crashed(), "parser crashed");
            printCounter(writer, summary.tlc().passed(), "TLC passed");
            printCounter(writer, summary.tlc().failed(), "TLC failed");
            printCounter(writer, summary.tlc().crashed(), "TLC crashed");
            writer.printf("[%20s %-18s]%n", summary.stopReason(), "stop reason");
        }
        return output.toString();
    }

    private static void printCounter(PrintWriter writer, long value, String label) {
        writer.printf("[%20d %-18s]%n", value, label);
    }

    private static void printGenerationCounters(PrintWriter writer, PbtStageSummary generator) {
        if (generator.added() == 0) {
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
