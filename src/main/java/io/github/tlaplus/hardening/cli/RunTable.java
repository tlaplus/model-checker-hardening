package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import io.github.tlaplus.hardening.workflow.WorkflowRunSummary;
import io.github.tlaplus.hardening.workflow.input.PbtStageSummary;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Duration;
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
            printCounter(writer, progress.awaitingApalache(), "awaiting Apalache");
            printCounter(writer, progress.generator().generated(), "generated inputs");
            printGenerationCounters(writer, progress.generator());
            printElapsed(writer, progress.generator().elapsed(), "generator elapsed");
            printCounter(writer, progress.parser().passed(), "parser passed");
            printCounter(writer, progress.parser().failed(), "parser failed");
            printCounter(writer, progress.parser().crashed(), "parser crashed");
            printElapsed(writer, progress.parser().elapsed(), "parser elapsed");
            printCounter(writer, progress.tlc().passed(), "TLC passed");
            printCounter(writer, progress.tlc().failed(), "TLC failed");
            printCounter(writer, progress.tlc().crashed(), "TLC crashed");
            printElapsed(writer, progress.tlc().elapsed(), "TLC elapsed");
            printCounter(writer, progress.apalache().passed(), "Apalache passed");
            printCounter(writer, progress.apalache().failed(), "Apalache failed");
            printCounter(writer, progress.apalache().crashed(), "Apalache crashed");
            printElapsed(writer, progress.apalache().elapsed(), "Apalache elapsed");
            printElapsed(writer, progress.totalElapsed(), "total elapsed");
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
            printCounter(writer, summary.corpus().apalacheInputEntries(), "awaiting Apalache");
            printCounter(writer, summary.generator().generated(), "generated inputs");
            printGenerationCounters(writer, summary.generator());
            printElapsed(writer, summary.generator().elapsed(), "generator elapsed");
            printCounter(writer, summary.parser().passed(), "parser passed");
            printCounter(writer, summary.parser().failed(), "parser failed");
            printCounter(writer, summary.parser().crashed(), "parser crashed");
            printElapsed(writer, summary.parser().elapsed(), "parser elapsed");
            printCounter(writer, summary.tlc().passed(), "TLC passed");
            printCounter(writer, summary.tlc().failed(), "TLC failed");
            printCounter(writer, summary.tlc().crashed(), "TLC crashed");
            printElapsed(writer, summary.tlc().elapsed(), "TLC elapsed");
            printCounter(writer, summary.apalache().passed(), "Apalache passed");
            printCounter(writer, summary.apalache().failed(), "Apalache failed");
            printCounter(writer, summary.apalache().crashed(), "Apalache crashed");
            printElapsed(writer, summary.apalache().elapsed(), "Apalache elapsed");
            printElapsed(writer, summary.totalElapsed(), "total elapsed");
            writer.printf("[%20s %-18s]%n", summary.stopReason(), "stop reason");
        }
        return output.toString();
    }

    private static void printCounter(PrintWriter writer, long value, String label) {
        writer.printf("[%20d %-18s]%n", value, label);
    }

    private static void printGenerationCounters(PrintWriter writer, PbtStageSummary generator) {
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
