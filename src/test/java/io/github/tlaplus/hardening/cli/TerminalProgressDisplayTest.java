package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import org.jline.builtins.ScreenTerminal;
import org.jline.terminal.Size;
import org.jline.utils.InfoCmp.Capability;
import org.jline.utils.Curses;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.junit.jupiter.api.Test;

class TerminalProgressDisplayTest {
    @Test
    void resizingReplacesTheWholeScreenAndClosingLeavesTheAlternateScreen() throws Exception {
        try (var fixture = RunTestTerminal.open("xterm-256color")) {
            var terminal = fixture.terminal();
            var screen = new ScreenTerminal(80, 24);
            screen.write("Random seed: 42\r\n");
            var display = new TerminalProgressDisplay(terminal, true, RunPalette.PLAIN, () -> 0);
            display.update(RunDisplayFixture.sample());
            screen.write(fixture.drain());
            assertTrue(screen.toString().contains("PARSER"), screen.toString());
            assertTrue(screen.toString().contains("both non-crash results"), screen.toString());

            terminal.setSize(new Size(60, 16));
            screen.setSize(60, 16);
            display.resize();
            screen.write(fixture.drain());
            assertTrue(screen.toString().contains("Stage"), screen.toString());
            assertFalse(screen.toString().contains("both non-crash results"), screen.toString());
            assertTrue(screen.toString().contains("Full statistics at completion"), screen.toString());

            terminal.setSize(new Size(80, 24));
            screen.setSize(80, 24);
            display.resize();
            screen.write(fixture.drain());
            assertTrue(screen.toString().contains("both non-crash results"), screen.toString());
            assertFalse(screen.toString().contains("Full statistics at completion"), screen.toString());

            display.close();
            var cleanup = fixture.drain();
            screen.write(cleanup);
            assertTrue(cleanup.contains(Curses.tputs(terminal.getStringCapability(Capability.exit_ca_mode))));
            // ScreenTerminal.setSize discards its inactive buffer. Test preservation separately.
            assertFalse(screen.toString().contains("PARSER"), screen.toString());
            display.close();
            display.update(RunDisplayFixture.sample());
            assertEquals("", fixture.drain());
        }
    }

    @Test
    void errorCleanupPreservesEarlierOutputAndIsSafeBeforeTheFirstSnapshot() throws Exception {
        try (var fixture = RunTestTerminal.open("xterm-256color")) {
            var screen = new ScreenTerminal(80, 24);
            screen.write("Random seed: 42\r\n");
            try (var display = new TerminalProgressDisplay(fixture.terminal(), false, RunPalette.PLAIN, () -> 0)) {
                display.update(RunDisplayFixture.sample());
                screen.write(fixture.drain());
                assertFalse(screen.toString().contains("Random seed: 42"));
            }
            screen.write(fixture.drain());
            assertTrue(screen.toString().contains("Random seed: 42"), screen.toString());
            assertFalse(screen.toString().contains("PARSER"));
        }
        try (var fixture = RunTestTerminal.open("xterm-256color")) {
            new TerminalProgressDisplay(fixture.terminal(), false, RunPalette.PLAIN, () -> 0).close();
            assertEquals("", fixture.drain());
        }
    }

    @Test
    void monochromeChangesRemainEmphasizedAndExpireEvenAcrossResizing() throws Exception {
        try (var fixture = RunTestTerminal.open("xterm", true)) {
            var terminal = fixture.terminal();
            var clock = new AtomicLong();
            assertTrue(RunPalette.detect(terminal, null).bold());
            try (var display = new TerminalProgressDisplay(terminal, true,
                    RunPalette.detect(terminal, null), clock::get)) {
                display.update(RunDisplayFixture.empty());
                fixture.drain();
                clock.set(1_000_000_000L);
                display.update(RunDisplayFixture.sample());
                var changed = fixture.drain();
                assertEquals(AttributedStyle.DEFAULT.bold(), firstCountStyle(changed));
                assertTrue(changed.contains("1240\u2191"), changed);
                assertFalse(changed.contains("[7m"), "changes never reverse the background");
                assertFalse(Pattern.compile("\u001b\\[[0-9;]*3[0-9][;m]")
                        .matcher(changed).find());
                terminal.setSize(new Size(60, 16));
                display.resize();
                assertEquals(AttributedStyle.DEFAULT.bold(), firstCountStyle(fixture.drain()));
                clock.set(2_000_000_000L);
                display.update(RunDisplayFixture.sample());
                var settled = fixture.drain();
                assertTrue(settled.contains("1240"));
                assertEquals(AttributedStyle.DEFAULT, firstCountStyle(settled));
                assertFalse(settled.contains("\u2191"), settled);
            }
        }
    }

    private static AttributedStyle firstCountStyle(String output) {
        var decoded = AttributedString.fromAnsi(output);
        return decoded.styleAt(decoded.toString().indexOf("1240"));
    }

    @Test
    void finalReportGoesToTheConfiguredWriterAfterRestoringTheScreen() throws Exception {
        try (var fixture = RunTestTerminal.open("xterm-256color")) {
            var clock = new AtomicLong();
            var display = new TerminalProgressDisplay(fixture.terminal(), true,
                    new RunPalette(true, true, Glyphs.UNICODE), clock::get);
            var configured = new StringWriter();
            try (var output = new RunOutput(new PrintWriter(configured), Optional.of(display))) {
                display.update(RunDisplayFixture.empty());
                clock.set(1_000_000_000L);
                display.update(RunDisplayFixture.sample());
                fixture.drain();
                output.finish(Path.of("corpus"), RunDisplayFixture.finished(RunDisplayFixture.sample()));
                var terminalOutput = fixture.drain();
                assertTrue(terminalOutput.contains(Curses.tputs(
                        fixture.terminal().getStringCapability(Capability.exit_ca_mode))));
                assertFalse(terminalOutput.contains("Workflow run finished"));
                var report = configured.toString();
                assertTrue(report.contains("Workflow run finished"));
                assertTrue(AttributedString.fromAnsi(report).toString().contains("Corpus entries: 1240"));
                assertTrue(report.contains("\u001b["), "the report keeps the terminal palette");
                assertFalse(report.contains("\u2191"), "transient markers do not reach the report");
                display.update(RunDisplayFixture.empty());
                assertEquals("", fixture.drain());
            }
        }
    }

    @Test
    void plainOutputHasNoEscapes() {
        var configured = new StringWriter();
        try (var output = RunOutput.open(new PrintWriter(configured), TerminalAcquisition.NONE, true)) {
            output.finish(Path.of("corpus"), RunDisplayFixture.finished(RunDisplayFixture.sample()));
        }
        assertTrue(configured.toString().contains("Corpus entries: 1240" + System.lineSeparator()));
        assertFalse(configured.toString().contains("\u001b"));
    }

    @Test
    void unknownSizeRecoversOnTheNextSnapshotWithoutEnteringTheScreenEarly() throws Exception {
        try (var fixture = RunTestTerminal.open("xterm-256color")) {
            var terminal = fixture.terminal();
            terminal.setSize(new Size(0, 0));
            try (var display = new TerminalProgressDisplay(terminal, true, RunPalette.PLAIN, () -> 0)) {
                display.update(RunDisplayFixture.sample());
                assertEquals("", fixture.drain());
                terminal.setSize(new Size(80, 24));
                display.update(RunDisplayFixture.sample());
                var output = fixture.drain();
                assertTrue(output.contains(Curses.tputs(terminal.getStringCapability(Capability.enter_ca_mode))));
                assertTrue(output.contains("PARSER"), output);
            }
        }
    }

    @Test
    void renderingFailureStopsLiveUpdatesAndRestoresTheScreen() throws Exception {
        try (var fixture = RunTestTerminal.open("xterm-256color")) {
            var calls = new AtomicLong();
            var failAt = new AtomicLong(Long.MAX_VALUE);
            // Each update reads the clock twice; the second read happens while rendering.
            LongSupplier clock = () -> {
                if (calls.incrementAndGet() == failAt.get()) {
                    throw new IllegalStateException("rendering");
                }
                return 0;
            };
            try (var display = new TerminalProgressDisplay(fixture.terminal(), true, RunPalette.PLAIN, clock)) {
                display.update(RunDisplayFixture.sample());
                fixture.drain();
                failAt.set(calls.get() + 2);
                display.update(RunDisplayFixture.sample());
                assertTrue(fixture.drain().contains(Curses.tputs(
                        fixture.terminal().getStringCapability(Capability.exit_ca_mode))));
                display.resize();
                display.update(RunDisplayFixture.sample());
                assertEquals("", fixture.drain());
                assertEquals(RunPalette.PLAIN, display.finish());
            }
        }
    }
}
