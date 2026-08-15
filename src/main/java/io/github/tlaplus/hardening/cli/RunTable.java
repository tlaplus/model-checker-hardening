package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import io.github.tlaplus.hardening.workflow.WorkflowRunSummary;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

/** Formats live and final workflow counters with one stable table layout. */
final class RunTable {
    private RunTable() {}

    static String progress(WorkflowProgress progress) {
        var output = new StringWriter();
        try (var writer = new PrintWriter(output)) {
            writer.printf("Workflow run in progress%n%n");
            printCounter(writer, progress.generator().seed(), "random seed");
            printCounter(writer, progress.corpusEntries(), "corpus entries");
            printCounter(writer, progress.remainingInputs(), "remaining inputs");
            printCounter(writer, progress.generator().added(), "generated inputs");
            printCounter(writer, progress.parser().passed(), "parser passed");
            printCounter(writer, progress.parser().failed(), "parser failed");
            printCounter(writer, progress.parser().crashed(), "parser crashed");
            writer.printf("[%20s %-18s]%n", progress.phase(), "run state");
        }
        return output.toString();
    }

    static String finished(Path corpus, WorkflowRunSummary summary) {
        var output = new StringWriter();
        try (var writer = new PrintWriter(output)) {
            writer.printf("Workflow run finished for '%s'%n%n", corpus);
            printCounter(writer, summary.generator().seed(), "random seed");
            printCounter(writer, summary.corpus().totalEntries(), "corpus entries");
            printCounter(writer, summary.corpus().inputEntries(), "remaining inputs");
            printCounter(writer, summary.generator().added(), "generated inputs");
            printCounter(writer, summary.parser().passed(), "parser passed");
            printCounter(writer, summary.parser().failed(), "parser failed");
            printCounter(writer, summary.parser().crashed(), "parser crashed");
            writer.printf("[%20s %-18s]%n", summary.stopReason(), "stop reason");
        }
        return output.toString();
    }

    private static void printCounter(PrintWriter writer, long value, String label) {
        writer.printf("[%20d %-18s]%n", value, label);
    }
}
