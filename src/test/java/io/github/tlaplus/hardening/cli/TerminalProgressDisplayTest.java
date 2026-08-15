package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.workflow.ParserStageSummary;
import io.github.tlaplus.hardening.workflow.PbtStageSummary;
import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class TerminalProgressDisplayTest {
    @Test
    void replacesLiveTablesAndLeavesOnlyTheFinalTableVisible() {
        var output = new StringWriter();
        var display = new TerminalProgressDisplay(new PrintWriter(output));
        var first = RunTable.running(progress(1, 0));
        var second = RunTable.running(progress(3, 2));
        var finished = "finished" + System.lineSeparator();

        display.update(progress(1, 0));
        display.update(progress(3, 2));
        display.finish(finished);
        display.close();

        assertEquals(
                first
                        + erase(first)
                        + second
                        + erase(second)
                        + finished,
                output.toString());
    }

    @Test
    void clearsAnUnfinishedTableBeforeAnErrorIsPrinted() {
        var output = new StringWriter();
        var display = new TerminalProgressDisplay(new PrintWriter(output));
        var table = RunTable.running(progress(2, 1));

        display.update(progress(2, 1));
        display.close();
        display.close();

        assertEquals(table + erase(table), output.toString());
    }

    private WorkflowProgress progress(long generated, long parsed) {
        return new WorkflowProgress(
                new PbtStageSummary(42, 0, generated, generated, 0, 0),
                new ParserStageSummary(parsed, 0, 0),
                generated,
                generated - parsed);
    }

    private String erase(String table) {
        return "\u001b[" + table.lines().count() + "F\u001b[J";
    }
}
