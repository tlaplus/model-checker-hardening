package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.WorkflowRunSummary;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jline.utils.AttributedString;

/** Complete, exact statistics grouped by stage; suitable for scrollback and redirected output. */
final class RunTable {
    private static final String INDENT = "  ";

    private RunTable() {}

    static List<AttributedString> finished(Path corpus, WorkflowRunSummary summary, RunPalette palette) {
        var text = new RunText(RunView.finished(summary).metrics(), Precision.EXACT, palette, RunText.Highlights.NONE);
        var lines = new ArrayList<AttributedString>();
        lines.add(text.line().heading("Workflow run finished for '" + corpus + "'").build());
        lines.add(text.line().text("Counts and elapsed times are cumulative across corpus runs.").build());
        fields(lines, text, RunMetric.Field.Section.SUMMARY, "");
        lines.add(labeled(text, "", "Stop reason", RunMetric.State.STATUS));
        lines.add(AttributedString.EMPTY);
        lines.add(text.line().heading(RunText.INPUTS_HEADING).build());
        fields(lines, text, RunMetric.Field.Section.INPUTS, INDENT);
        for (var stage : CorpusStage.values()) {
            lines.add(AttributedString.EMPTY);
            lines.add(text.line().stage(stage).build());
            lines.add(labeled(text, INDENT, RunMetric.Queue.LABEL, new RunMetric.Queue(stage)));
            for (var verdict : stage.resultVerdicts()) {
                lines.add(text.line().text(INDENT).result(stage, verdict).build());
            }
            lines.add(labeled(text, INDENT, RunMetric.StageElapsed.LABEL, new RunMetric.StageElapsed(stage)));
        }
        lines.add(text.line().text(RunText.COUNTEREXAMPLE_LEGEND
                + "; Agree/Differ: checker conformance; Keep/Drop: quality selection.").build());
        lines.add(text.line().text(RunMetric.Queue.LABEL + " counts are the final inventory.").build());
        return List.copyOf(lines);
    }

    private static void fields(
            List<AttributedString> lines, RunText text, RunMetric.Field.Section section, String indent) {
        for (var field : RunMetric.Field.values()) {
            if (field.section() == section) {
                lines.add(labeled(text, indent, field.label(), field));
            }
        }
    }

    private static AttributedString labeled(RunText text, String indent, String label, RunMetric key) {
        return text.line().text(indent + label + ": ").value(key).build();
    }
}
