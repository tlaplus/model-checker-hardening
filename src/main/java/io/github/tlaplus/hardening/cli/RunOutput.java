package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.WorkflowRunSummary;
import io.github.tlaplus.hardening.workflow.WorkflowRunner;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.Optional;

/**
 * The output of one run: the command's configured writer and an optional live display in front of
 * it. The final report always goes to the configured writer, after the display has restored the
 * normal screen. Closing restores the screen as well, so a diagnostic printed after the resource
 * scope lands in scrollback.
 */
final class RunOutput implements AutoCloseable {
    private final PrintWriter output;
    private final Optional<TerminalProgressDisplay> display;

    RunOutput(PrintWriter output, Optional<TerminalProgressDisplay> display) {
        this.output = output;
        this.display = display;
    }

    static RunOutput open(PrintWriter output, TerminalAcquisition terminals, boolean feedback) {
        return new RunOutput(output, terminals.open(feedback));
    }

    /** Runs the workflow, subscribing to progress only when a live display shows it. */
    WorkflowRunSummary run(WorkflowRunner runner, CorpusDirectory corpus, long seed, int maximumCpus)
            throws IOException, CorpusException, WorkflowException {
        if (display.isPresent()) {
            return runner.run(corpus, seed, maximumCpus, display.get()::update);
        }
        return runner.run(corpus, seed, maximumCpus);
    }

    void finish(Path corpus, WorkflowRunSummary summary) {
        var palette = display.map(TerminalProgressDisplay::finish).orElse(RunPalette.PLAIN);
        for (var line : RunTable.finished(corpus, summary, palette)) {
            output.println(line.toAnsi());
        }
        output.flush();
    }

    @Override
    public void close() {
        display.ifPresent(TerminalProgressDisplay::close);
    }
}
