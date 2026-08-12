package io.github.tlaplus.hardening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.cli.FuzzTlaCommand;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class MainTest {
    @Test
    void printsHelpWhenNoArgumentsAreGiven() {
        var result = execute();

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertTrue(result.out().contains("Usage: fuzztla"));
        assertTrue(result.out().contains("init"));
        assertTrue(result.out().contains("print"));
        assertTrue(result.out().contains("run"));
        assertEquals("", result.err());
    }

    @Test
    void supportsStandardHelpOption() {
        var result = execute("--help");

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertTrue(result.out().contains("Usage: fuzztla"));
        assertTrue(result.out().contains("Synthesize TLA+ specifications"));
        assertEquals("", result.err());
    }

    @Test
    void reportsDevelopmentVersionWhenRunningFromClasses() {
        var result = execute("--version");

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertEquals("fuzztla development" + System.lineSeparator(), result.out());
        assertEquals("", result.err());
    }

    @Test
    void rejectsUnknownOptions() {
        var result = execute("--unknown");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
        assertEquals("", result.out());
        assertTrue(result.err().contains("Unknown option: '--unknown'"));
        assertTrue(result.err().contains("Usage: fuzztla"));
    }

    @Test
    void printsInitHelp() {
        var result = execute("init", "--help");

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertTrue(result.out().contains("Usage: fuzztla init [-h]"));
        assertFalse(result.out().contains("--version"));
        assertEquals("", result.err());
    }

    @Test
    void reportsInitAsNotImplemented() {
        var result = execute("init");

        assertEquals(CommandLine.ExitCode.SOFTWARE, result.exitCode());
        assertEquals("", result.out());
        assertEquals(
                "fuzztla: init is not implemented yet" + System.lineSeparator(), result.err());
    }

    @Test
    void rejectsArgumentsToInit() {
        var result = execute("init", "project");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
        assertTrue(result.err().contains("Unmatched argument at index 1: 'project'"));
    }

    @Test
    void rejectsUnknownInitOptions() {
        var result = execute("init", "--force");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
        assertTrue(result.err().contains("Unknown option: '--force'"));
    }

    @Test
    void printsRunHelp() {
        var result = execute("run", "--help");

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertTrue(result.out().contains("Usage: fuzztla run [-h] --how=TECHNIQUE"));
        assertTrue(result.out().contains("currently: pbt"));
        assertFalse(result.out().contains("--version"));
        assertEquals("", result.err());
    }

    @Test
    void requiresTechnique() {
        var result = execute("run");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
        assertTrue(result.err().contains("Missing required option: '--how=TECHNIQUE'"));
    }

    @Test
    void rejectsUnsupportedTechnique() {
        var result = execute("run", "--how=random");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
        assertTrue(result.err().contains("expected one of: pbt"));
    }

    @Test
    void reportsPbtAsNotImplemented() {
        var result = execute("run", "--how=pbt");

        assertEquals(CommandLine.ExitCode.SOFTWARE, result.exitCode());
        assertEquals("", result.out());
        assertEquals(
                "fuzztla: run --how=pbt is not implemented yet" + System.lineSeparator(),
                result.err());
    }

    @Test
    void rejectsArgumentsToRun() {
        var result = execute("run", "--how=pbt", "spec.tla");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
        assertTrue(result.err().contains("Unmatched argument at index 2: 'spec.tla'"));
    }

    @Test
    void rejectsUnknownRunOptions() {
        var result = execute("run", "--how=pbt", "--seed=42");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
        assertTrue(result.err().contains("Unknown option: '--seed=42'"));
    }

    @Test
    void printsPrintHelp() {
        var result = execute("print", "--help");

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertTrue(result.out().contains("Usage: fuzztla print [-h] FILE"));
        assertTrue(result.out().contains("Binary generator input"));
        assertEquals("", result.err());
    }

    @Test
    void requiresPrintInput() {
        var result = execute("print");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
        assertTrue(result.err().contains("Missing required parameter: 'FILE'"));
    }

    @Test
    void printsExpressionFromEmptyFile(@TempDir Path directory) throws Exception {
        var input = directory.resolve("empty.bin");
        java.nio.file.Files.createFile(input);

        var result = execute("print", input.toString());

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertEquals("FALSE" + System.lineSeparator(), result.out());
        assertEquals("", result.err());
    }

    @Test
    void reportsUnreadablePrintInput(@TempDir Path directory) {
        var missing = directory.resolve("missing.bin");

        var result = execute("print", missing.toString());

        assertEquals(CommandLine.ExitCode.SOFTWARE, result.exitCode());
        assertEquals("", result.out());
        assertTrue(result.err().contains("fuzztla: cannot read"));
        assertTrue(result.err().contains("missing.bin"));
    }

    @Test
    void rejectsExtraPrintArguments(@TempDir Path directory) throws Exception {
        var input = directory.resolve("input.bin");
        java.nio.file.Files.createFile(input);

        var result = execute("print", input.toString(), "extra");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
        assertTrue(result.err().contains("Unmatched argument"));
    }

    private Result execute(String... args) {
        var out = new ByteArrayOutputStream();
        var err = new ByteArrayOutputStream();
        var commandLine = new CommandLine(new FuzzTlaCommand());
        commandLine.setOut(new PrintWriter(out, true, StandardCharsets.UTF_8));
        commandLine.setErr(new PrintWriter(err, true, StandardCharsets.UTF_8));

        var exitCode = commandLine.execute(args);
        return new Result(
                exitCode,
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8));
    }

    private record Result(int exitCode, String out, String err) {}
}
