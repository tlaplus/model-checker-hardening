package io.github.tlaplus.hardening.cli;

import java.util.List;
import java.util.function.Function;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.utils.AttributedString;
import org.jline.utils.InfoCmp.Capability;

/** Owns the alternate screen: entering it, updating changed rows, and restoring scrollback. */
final class ProgressScreen {
    private final Terminal terminal;
    private List<AttributedString> previous = List.of();
    private Size size;
    private Size drawnSize;
    private boolean entered;

    ProgressScreen(Terminal terminal) {
        this.terminal = terminal;
    }

    /** Rereads the terminal size, which is otherwise cached between refreshes. */
    void resized() {
        size = terminal.getSize();
    }

    /**
     * Shows the layout for the current size, entering the alternate screen on first use. An unknown
     * or unusable size is reread on every call, so recovery does not depend on a resize signal.
     */
    void show(Function<Size, List<AttributedString>> layout) {
        if (!usable(size)) {
            resized();
        }
        if (!usable(size)) {
            return;
        }
        if (!entered) {
            entered = true;
            terminal.puts(Capability.enter_ca_mode);
            terminal.puts(Capability.cursor_invisible);
        }
        var lines = layout.apply(size);
        if (!size.equals(drawnSize)) {
            terminal.puts(Capability.clear_screen);
            previous = List.of();
            drawnSize = new Size(size.getColumns(), size.getRows());
        }
        for (var row = 0; row < Math.max(lines.size(), previous.size()); row++) {
            var line = row < lines.size() ? lines.get(row) : AttributedString.EMPTY;
            var old = row < previous.size() ? previous.get(row) : AttributedString.EMPTY;
            if (!line.equals(old)) {
                terminal.puts(Capability.cursor_address, row, 0);
                // JLine 3.25's Display strips all attributes below eight colors. Writing attributed
                // rows directly preserves reverse video and bold on monochrome terminals too.
                terminal.writer().print(line.toAnsi(terminal));
                terminal.puts(Capability.clr_eol);
            }
        }
        terminal.puts(Capability.cursor_address, lines.size(), 0);
        terminal.flush();
        previous = List.copyOf(lines);
    }

    /** Cursor addressing needs at least two columns and rows; zero means unknown. */
    private static boolean usable(Size size) {
        return size != null && size.getColumns() >= 2 && size.getRows() >= 2;
    }

    /** Restores the normal screen and its scrollback; does nothing if never entered. */
    void leave() {
        if (entered) {
            terminal.puts(Capability.exit_attribute_mode);
            terminal.puts(Capability.cursor_normal);
            terminal.puts(Capability.exit_ca_mode);
            terminal.flush();
            entered = false;
            drawnSize = null;
            previous = List.of();
        }
    }
}
