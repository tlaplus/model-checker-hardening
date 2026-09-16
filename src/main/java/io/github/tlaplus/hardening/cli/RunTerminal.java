package io.github.tlaplus.hardening.cli;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;

/** Acquires the process stdout terminal for live progress when it is capable. */
final class RunTerminal {
    private RunTerminal() {}

    static Optional<TerminalProgressDisplay> open(boolean feedback) {
        var console = System.console();
        if (console == null || !console.isTerminal() || "dumb".equalsIgnoreCase(System.getenv("TERM"))) {
            return Optional.empty();
        }
        Terminal terminal = null;
        try {
            terminal = TerminalBuilder.builder().system(true)
                    .systemOutput(TerminalBuilder.SystemOutput.SysOut)
                    // The bundled JNI backend loses baud-rate bits when restoring POSIX termios.
                    // The exec provider changes only represented attributes and preserves them.
                    .provider("exec").dumb(false).paused(true).build();
            if (!supportsDisplay(terminal)) {
                closeQuietly(terminal);
                return Optional.empty();
            }
            return Optional.of(new TerminalProgressDisplay(terminal, feedback,
                    RunPalette.detect(terminal, System.getenv("NO_COLOR")), System::nanoTime));
        } catch (IOException | RuntimeException exception) {
            if (terminal != null) {
                closeQuietly(terminal);
            }
            return Optional.empty();
        }
    }

    static boolean supportsDisplay(Terminal terminal) {
        return List.of(Capability.enter_ca_mode, Capability.exit_ca_mode,
                        Capability.cursor_address, Capability.clear_screen, Capability.clr_eol)
                .stream().allMatch(capability -> terminal.getStringCapability(capability) != null);
    }

    /** Terminal cleanup is best effort: it must not replace the workflow's result or diagnostic. */
    static void closeQuietly(Terminal terminal) {
        try {
            terminal.close();
        } catch (IOException ignored) {
            // The report or diagnostic that follows is more useful than a cleanup failure.
        }
    }
}
