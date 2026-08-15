package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import java.io.PrintWriter;
import java.util.Objects;

/** Maintains one in-place workflow progress block on an ANSI terminal. */
final class TerminalProgressDisplay implements AutoCloseable {
    private static final String ERASE_TO_END = "\u001b[J";

    private final PrintWriter output;
    private int renderedLines;
    private boolean finished;
    private boolean closed;

    TerminalProgressDisplay(PrintWriter output) {
        this.output = Objects.requireNonNull(output, "output");
    }

    synchronized void update(WorkflowProgress progress) {
        if (!finished && !closed) {
            replace(RunTable.running(progress));
        }
    }

    synchronized void finish(String summary) {
        if (!finished && !closed) {
            replace(Objects.requireNonNull(summary, "summary"));
            finished = true;
        }
    }

    private void replace(String text) {
        eraseRenderedBlock();
        output.print(text);
        output.flush();
        renderedLines = Math.toIntExact(text.lines().count());
    }

    private void eraseRenderedBlock() {
        if (renderedLines > 0) {
            output.printf("\u001b[%dF%s", renderedLines, ERASE_TO_END);
            renderedLines = 0;
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (!finished) {
            eraseRenderedBlock();
            output.flush();
        }
        closed = true;
    }
}
