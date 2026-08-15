package io.github.tlaplus.hardening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.cli.FuzzTlaCommand;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.ParserConfig;
import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.config.StageConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.config.WorkflowConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusInputCodec;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
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
        assertTrue(result.out().contains("Usage: fuzztla init"));
        assertTrue(result.out().contains("--corpus=DIR"));
        assertFalse(result.out().contains("--version"));
        assertEquals("", result.err());
    }

    @Test
    void initializesDefaultConfigurationAndInputDirectory(@TempDir Path directory)
            throws Exception {
        var corpus = directory.resolve("custom-corpus");

        var result = execute("init", "--corpus=" + corpus);

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertTrue(result.out().contains(corpus.toString()));
        assertEquals("", result.err());
        assertEquals(
                FuzzTlaConfig.defaults(),
                TomlConfig.read(corpus.resolve(CorpusDirectory.CONFIG_FILE_NAME)));
        assertTrue(Files.isDirectory(corpus.resolve(CorpusDirectory.INPUT_DIRECTORY_NAME)));
        assertTrue(Files.isDirectory(
                corpus.resolve(CorpusDirectory.PARSER_PASS_DIRECTORY_NAME)));
        assertTrue(Files.isDirectory(
                corpus.resolve(CorpusDirectory.PARSER_FAIL_DIRECTORY_NAME)));
        assertTrue(Files.isDirectory(
                corpus.resolve(CorpusDirectory.PARSER_CRASH_DIRECTORY_NAME)));
    }

    @Test
    void initializesInsideAnExistingDirectoryWithoutRemovingContents(@TempDir Path directory)
            throws Exception {
        var corpus = directory.resolve("corpus");
        Files.createDirectories(corpus.resolve(CorpusDirectory.INPUT_DIRECTORY_NAME));
        var marker = corpus.resolve("keep-me");
        Files.writeString(marker, "preserved");

        var result = execute("init", "--corpus=" + corpus);

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertEquals("preserved", Files.readString(marker));
        assertTrue(Files.isRegularFile(corpus.resolve(CorpusDirectory.CONFIG_FILE_NAME)));
    }

    @Test
    void refusesToOverwriteExistingConfiguration(@TempDir Path directory) {
        var corpus = directory.resolve("corpus");
        assertEquals(
                CommandLine.ExitCode.OK,
                execute("init", "--corpus=" + corpus).exitCode());

        var result = execute("init", "--corpus=" + corpus);

        assertEquals(CommandLine.ExitCode.SOFTWARE, result.exitCode());
        assertTrue(result.err().contains("configuration already exists"));
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
        assertTrue(result.out().contains("Usage: fuzztla run"));
        assertTrue(result.out().contains("--corpus=DIR"));
        assertTrue(result.out().contains("--seed=SEED"));
        assertTrue(result.out().contains("--max-cpus=N"));
        assertTrue(result.out().contains("Nonnegative 64-bit seed"));
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
    void generatesAValidHashedCorpus(@TempDir Path directory) throws Exception {
        var corpus = initializeSmallCorpus(directory, 8, 32);

        var result = execute(
                "run",
                "--how=pbt",
                "--corpus=" + corpus,
                "--seed=42",
                "--max-cpus=1");

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertEquals("", result.err());
        assertFalse(result.out().contains("Workflow run in progress"));
        assertFalse(result.out().contains("\u001b["));
        assertTrue(result.out().contains("Workflow run finished for '"));
        assertTrue(result.out().lines()
                .anyMatch(line -> line.matches("\\[\\s+42 random seed\\s+]")));
        assertTrue(result.out().lines()
                .anyMatch(line -> line.matches("\\[\\s+8 corpus entries\\s+]")));
        assertTrue(result.out().lines()
                .anyMatch(line -> line.matches("\\[\\s+8 generated inputs\\s+]")));
        var entryCount = 0;
        for (var resultDirectory : java.util.List.of(
                CorpusDirectory.PARSER_PASS_DIRECTORY_NAME,
                CorpusDirectory.PARSER_FAIL_DIRECTORY_NAME,
                CorpusDirectory.PARSER_CRASH_DIRECTORY_NAME)) {
            try (var paths = Files.list(corpus.resolve(resultDirectory))) {
                for (var entry : paths.toList()) {
                    entryCount++;
                    var corpusInput = CorpusInputCodec.decode(Files.readAllBytes(entry));
                    assertEquals(CorpusInput.Kind.EXPRESSION, corpusInput.kind());
                    assertEquals(
                            hash(corpusInput.input()) + ".cbor",
                            entry.getFileName().toString());
                }
            }
        }
        assertEquals(8, entryCount);
        var config = TomlConfig.read(corpus.resolve(CorpusDirectory.CONFIG_FILE_NAME));
        assertEquals(
                8,
                CorpusDirectory.open(corpus).verify(IrGenerators.expressions(config.generator())));
    }

    @Test
    void treatsConfiguredCorpusSizeAsATotalTarget(@TempDir Path directory) throws Exception {
        var corpus = initializeSmallCorpus(directory, 4, 16);
        assertEquals(
                CommandLine.ExitCode.OK,
                execute(
                                "run",
                                "--how=pbt",
                                "--corpus=" + corpus,
                                "--seed=7",
                                "--max-cpus=1")
                        .exitCode());

        var result = execute(
                "run",
                "--how=pbt",
                "--corpus=" + corpus,
                "--seed=7",
                "--max-cpus=1");

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertEquals(
                summaryOutput(corpus, 7, 4),
                result.out());
    }

    @Test
    void reportsAnUninitializedCorpus(@TempDir Path directory) {
        var missing = directory.resolve("missing-corpus");
        var result = execute("run", "--how=pbt", "--corpus=" + missing, "--seed=1");

        assertEquals(CommandLine.ExitCode.SOFTWARE, result.exitCode());
        assertTrue(result.err().contains("corpus directory does not exist"));
    }

    @Test
    void verifiesExistingEntriesBeforeGenerating(@TempDir Path directory) throws Exception {
        var corpus = initializeSmallCorpus(directory, 3, 16);
        var inputDirectory = corpus.resolve(CorpusDirectory.INPUT_DIRECTORY_NAME);
        Files.write(inputDirectory.resolve("not-a-hash.cbor"), new byte[] {1});

        var result = execute("run", "--how=pbt", "--corpus=" + corpus, "--seed=1");

        assertEquals(CommandLine.ExitCode.SOFTWARE, result.exitCode());
        assertTrue(result.err().contains("invalid corpus entry name"));
        try (var entries = Files.list(inputDirectory)) {
            assertEquals(1, entries.count());
        }
    }

    @Test
    void rejectsArgumentsToRun() {
        var result = execute("run", "--how=pbt", "spec.tla");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
        assertTrue(result.err().contains("Unmatched argument"));
    }

    @Test
    void rejectsInvalidSeed() {
        var result = execute("run", "--how=pbt", "--seed=not-a-long");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
        assertTrue(result.err().contains("Invalid value for option '--seed'"));
        assertTrue(result.err().contains("0.." + Long.MAX_VALUE));
    }

    @Test
    void rejectsNegativeSeed() {
        var result = execute("run", "--how=pbt", "--seed=-1");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
        assertTrue(result.err().contains("0.." + Long.MAX_VALUE));
    }

    @Test
    void acceptsLargestNonnegativeSeed(@TempDir Path directory) throws Exception {
        var corpus = initializeSmallCorpus(directory, 0, 0);

        var result = execute(
                "run",
                "--how=pbt",
                "--corpus=" + corpus,
                "--seed=" + Long.MAX_VALUE,
                "--max-cpus=1");

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertEquals(
                summaryOutput(corpus, Long.MAX_VALUE, 0),
                result.out());
    }

    @Test
    void generatesANonnegativeSeedWhenNoneIsGiven(@TempDir Path directory) throws Exception {
        var corpus = initializeSmallCorpus(directory, 0, 0);

        var result = execute(
                "run", "--how=pbt", "--corpus=" + corpus, "--max-cpus=1");

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertTrue(result.out().lines()
                .anyMatch(line -> line.matches("\\[\\s+\\d+ random seed\\s+]")));
    }

    @Test
    void printsPrintHelp() {
        var result = execute("print", "--help");

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertTrue(result.out().contains("Usage: fuzztla print"));
        assertTrue(result.out().contains("--corpus=DIR"));
        assertTrue(result.out().contains("--spec"));
        assertTrue(result.out().contains("CBOR corpus input"));
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
        var input = directory.resolve("empty.cbor");
        Files.write(input, CorpusInputCodec.encode(CorpusInput.expression(new byte[0])));

        var result = execute("print", input.toString());

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertEquals("FALSE" + System.lineSeparator(), result.out());
        assertEquals("", result.err());
    }

    @Test
    void printsTheCompleteParserSpecification(@TempDir Path directory) throws Exception {
        var input = directory.resolve("empty.cbor");
        Files.write(input, CorpusInputCodec.encode(CorpusInput.expression(new byte[0])));

        var result = execute("print", "--spec", input.toString());

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertTrue(result.out().contains("MODULE FuzzInput"));
        assertTrue(result.out()
                .contains("EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants"));
        assertTrue(result.out().contains("VARIABLE exprValue"));
        assertTrue(result.out().contains("Init == exprValue = FALSE"));
        assertTrue(result.out().contains("Next == UNCHANGED exprValue"));
        assertTrue(result.out().contains("Inv == exprValue = FALSE"));
        assertEquals("", result.err());
    }

    @Test
    void rejectsRawAndUnsupportedPrintInputs(@TempDir Path directory) throws Exception {
        var raw = directory.resolve("raw.bin");
        Files.write(raw, new byte[] {0});
        var module = directory.resolve("module.cbor");
        Files.write(
                module,
                CorpusInputCodec.encode(
                        new CorpusInput(CorpusInput.Kind.MODULE, new byte[] {0})));

        var rawResult = execute("print", raw.toString());
        var moduleResult = execute("print", module.toString());

        assertEquals(CommandLine.ExitCode.SOFTWARE, rawResult.exitCode());
        assertTrue(rawResult.err().contains("cannot decode"));
        assertEquals(CommandLine.ExitCode.SOFTWARE, moduleResult.exitCode());
        assertTrue(moduleResult.err().contains("unsupported input kind 'module'"));
    }

    @Test
    void printsUsingCorpusGeneratorConfiguration(@TempDir Path directory) throws Exception {
        var corpus = initializeSmallCorpus(directory, 0, 0);
        var input = directory.resolve("empty.cbor");
        Files.write(input, CorpusInputCodec.encode(CorpusInput.expression(new byte[0])));

        var result = execute("print", "--corpus=" + corpus, input.toString());

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertEquals("FALSE" + System.lineSeparator(), result.out());
        assertEquals("", result.err());
    }

    @Test
    void reportsUnreadablePrintInput(@TempDir Path directory) {
        var missing = directory.resolve("missing.cbor");

        var result = execute("print", missing.toString());

        assertEquals(CommandLine.ExitCode.SOFTWARE, result.exitCode());
        assertEquals("", result.out());
        assertTrue(result.err().contains("fuzztla: cannot read"));
        assertTrue(result.err().contains("missing.cbor"));
    }

    @Test
    void reportsUnreadablePrintCorpus(@TempDir Path directory) throws Exception {
        var input = directory.resolve("input.cbor");
        Files.write(input, CorpusInputCodec.encode(CorpusInput.expression(new byte[0])));

        var result = execute("print", "--corpus=" + directory.resolve("missing"), input.toString());

        assertEquals(CommandLine.ExitCode.SOFTWARE, result.exitCode());
        assertTrue(result.err().contains("cannot read corpus"));
    }

    @Test
    void rejectsExtraPrintArguments(@TempDir Path directory) throws Exception {
        var input = directory.resolve("input.cbor");
        Files.write(input, CorpusInputCodec.encode(CorpusInput.expression(new byte[0])));

        var result = execute("print", input.toString(), "extra");

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
        assertTrue(result.err().contains("Unmatched argument"));
    }

    private Path initializeSmallCorpus(Path directory, int entries, int maximumInputBytes)
            throws Exception {
        var corpus = directory.resolve("corpus");
        assertEquals(
                CommandLine.ExitCode.OK,
                execute("init", "--corpus=" + corpus).exitCode());
        var config = new FuzzTlaConfig(
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        entries,
                        new StageConfig(entries),
                        new ParserConfig(entries, 10)),
                new PbtConfig(maximumInputBytes));
        Files.writeString(
                corpus.resolve(CorpusDirectory.CONFIG_FILE_NAME),
                TomlConfig.render(config),
                StandardCharsets.UTF_8);
        return corpus;
    }

    private String hash(byte[] input) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
    }

    private String summaryOutput(Path corpus, long seed, long total) {
        return """
                Workflow run finished for '%s'

                [%20d %-18s]
                [%20d %-18s]
                [%20d %-18s]
                [%20d %-18s]
                [%20d %-18s]
                [%20d %-18s]
                [%20d %-18s]
                [%20s %-18s]
                """
                .formatted(
                        corpus.toAbsolutePath().normalize(),
                        seed,
                        "random seed",
                        total,
                        "corpus entries",
                        0,
                        "remaining inputs",
                        0,
                        "generated inputs",
                        0,
                        "parser passed",
                        0,
                        "parser failed",
                        0,
                        "parser crashed",
                        "COMPLETED",
                        "stop reason");
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
