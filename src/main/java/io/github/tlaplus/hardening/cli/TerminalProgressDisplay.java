package io.github.tlaplus.hardening.cli;

import io.github.tlaplus.hardening.workflow.WorkflowProgress;
import java.io.IOError;
import java.util.Map;
import java.util.function.LongSupplier;
import org.jline.terminal.Terminal;

/**
 * Terminal mechanics of live progress: snapshots, change highlighting, resizing, and screen
 * restoration. It never writes the final report; its owner does, after {@link #finish}.
 */
final class TerminalProgressDisplay implements AutoCloseable {
    private final Terminal terminal;
    private final ProgressScreen screen;
    private final RunPalette palette;
    private final boolean feedback;
    private final LongSupplier clock;
    private final ProgressChanges changes = new ProgressChanges();
    private final Terminal.SignalHandler previousResize;
    private Map<RunMetric, RunValue> latest;
    private boolean stopped;
    private boolean closed;

    TerminalProgressDisplay(Terminal terminal, boolean feedback, RunPalette palette, LongSupplier clock) {
        this.terminal = terminal;
        this.feedback = feedback;
        this.palette = palette;
        this.clock = clock;
        screen = new ProgressScreen(terminal);
        previousResize = terminal.handle(Terminal.Signal.WINCH, _ -> resize());
    }

    synchronized void update(WorkflowProgress progress) {
        if (!stopped) {
            latest = RunView.live(progress).metrics();
            changes.observe(latest, clock.getAsLong());
            redraw();
        }
    }

    synchronized void resize() {
        if (!stopped) {
            screen.resized();
            if (latest != null) {
                redraw();
            }
        }
    }

    /**
     * Stops live rendering and restores the normal screen. Returns the palette the final report may
     * use on this terminal.
     */
    synchronized RunPalette finish() {
        stopped = true;
        attempt(screen::leave);
        return palette;
    }

    private void redraw() {
        try {
            var text = new RunText(latest, Precision.COMPACT, palette, changes.highlighted(clock.getAsLong()));
            screen.show(size -> ProgressLayout.render(text, size, feedback));
        } catch (RuntimeException | IOError exception) {
            // Live progress is optional: stop rendering and leave the final report available.
            stopped = true;
            attempt(screen::leave);
        }
    }

    /** Attempts every cleanup step even if an earlier one fails, and never throws. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        stopped = true;
        attempt(() -> terminal.handle(Terminal.Signal.WINCH, previousResize));
        attempt(screen::leave);
        RunTerminal.closeQuietly(terminal);
    }

    private static void attempt(Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | IOError ignored) {
            // A display failure must not replace the workflow's result or diagnostic.
        }
    }
}
