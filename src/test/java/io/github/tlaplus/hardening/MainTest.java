package io.github.tlaplus.hardening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.cli.FuzzTlaCommand;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.ParserStageConfig;
import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.config.StageConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.config.WorkflowConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEntryValidator;
import io.github.tlaplus.hardening.corpus.CorpusEnvelopeCodec;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusInputCodec;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.corpus.StageMetadata;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class MainTest {
    @Test
    void printsHelpWhenNoArgumentsAreGiven() {
        var result = execute();

        assertEquals(CommandLine.ExitCode.OK, result.exitCode(), result.err());
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
                TomlConfig.read(corpus.resolve(CorpusPath.CONFIG.relativePath())));
        assertTrue(Files.isDirectory(corpus.resolve(CorpusPath.INPUT.relativePath())));
        assertTrue(Files.isDirectory(corpus.resolve(CorpusPath.PARSER_PASS.relativePath())));
        assertTrue(Files.isDirectory(corpus.resolve(CorpusPath.PARSER_FAIL.relativePath())));
        assertTrue(Files.isDirectory(corpus.resolve(CorpusPath.PARSER_CRASH.relativePath())));
        assertTrue(Files.isDirectory(corpus.resolve(CorpusPath.TLC_INPUT.relativePath())));
        assertTrue(Files.isDirectory(corpus.resolve(CorpusPath.TLC_PASS.relativePath())));
        assertTrue(Files.isDirectory(corpus.resolve(CorpusPath.TLC_FAIL.relativePath())));
        assertTrue(Files.isDirectory(corpus.resolve(CorpusPath.TLC_CRASH.relativePath())));
        assertTrue(Files.isDirectory(corpus.resolve(CorpusPath.APALACHE_INPUT.relativePath())));
        assertTrue(Files.isDirectory(corpus.resolve(CorpusPath.APALACHE_PASS.relativePath())));
        assertTrue(Files.isDirectory(corpus.resolve(CorpusPath.APALACHE_FAIL.relativePath())));
        assertTrue(Files.isDirectory(corpus.resolve(CorpusPath.APALACHE_CRASH.relativePath())));
    }

    @Test
    void initializesInsideAnExistingDirectoryWithoutRemovingContents(@TempDir Path directory)
            throws Exception {
        var corpus = directory.resolve("corpus");
        Files.createDirectories(corpus.resolve(CorpusPath.INPUT.relativePath()));
        var marker = corpus.resolve("keep-me");
        Files.writeString(marker, "preserved");

        var result = execute("init", "--corpus=" + corpus);

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertEquals("preserved", Files.readString(marker));
        assertTrue(Files.isRegularFile(corpus.resolve(CorpusPath.CONFIG.relativePath())));
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

        assertEquals(CommandLine.ExitCode.OK, result.exitCode(), result.err());
        assertEquals("", result.err());
        assertTrue(result.out().startsWith("Random seed: 42" + System.lineSeparator()));
        assertFalse(result.out().contains("Workflow run in progress"));
        assertFalse(result.out().contains("\u001b["));
        assertTrue(result.out().contains("Workflow run finished for '"));
        assertFalse(result.out().contains("random seed"));
        assertTrue(result.out().lines()
                .anyMatch(line -> line.matches("\\[\\s+8 corpus entries\\s+]")));
        assertTrue(result.out().lines()
                .anyMatch(line -> line.matches("\\[\\s+8 generated inputs\\s+]")));
        var entryCount = 0;
        for (var resultDirectory : java.util.List.of(
                CorpusPath.APALACHE_PASS,
                CorpusPath.APALACHE_FAIL,
                CorpusPath.APALACHE_CRASH,
                CorpusPath.PARSER_FAIL,
                CorpusPath.PARSER_CRASH)) {
            try (var paths = Files.list(corpus.resolve(resultDirectory.relativePath()))) {
                for (var entry : paths
                        .filter(path -> path.getFileName().toString().endsWith(".cbor"))
                        .toList()) {
                    entryCount++;
                    var encoded = Files.readAllBytes(entry);
                    var corpusInput = CorpusInputCodec.decode(encoded);
                    var generation = CorpusEnvelopeCodec.decodeEnvelope(encoded)
                            .generation()
                            .orElseThrow();
                    assertEquals(CorpusInput.Kind.EXPRESSION, corpusInput.kind());
                    assertTrue(generation.cohort() >= 0 && generation.cohort() < 10);
                    assertTrue(generation.richness()
                            >= new PbtConfig(32, 10, 2.0, 1.5)
                                    .richnessThreshold(generation.cohort()));
                    assertEquals(
                            hash(corpusInput.input()) + ".cbor",
                            entry.getFileName().toString());
                }
            }
        }
        assertEquals(8, entryCount);
        var config = TomlConfig.read(corpus.resolve(CorpusPath.CONFIG.relativePath()));
        assertEquals(
                8,
                CorpusDirectory.openExisting(corpus)
                        .recoverAndValidate(CorpusEntryValidator.NONE)
                        .totalEntries());
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
        assertEquals("", result.err());
        assertTrue(result.out().startsWith("Random seed: 7" + System.lineSeparator()));
        assertTrue(result.out().lines()
                .anyMatch(line -> line.matches("\\[\\s+4 generated inputs\\s+]")));
        assertTrue(result.out().lines()
                .anyMatch(line -> line.contains("total elapsed")));
        assertTrue(result.out().contains("COMPLETED"));
    }

    @Test
    void reportsAnUninitializedCorpus(@TempDir Path directory) {
        var missing = directory.resolve("missing-corpus");
        var result = execute("run", "--how=pbt", "--corpus=" + missing, "--seed=1");

        assertEquals(CommandLine.ExitCode.SOFTWARE, result.exitCode());
        assertEquals("Random seed: 1" + System.lineSeparator(), result.out());
        assertTrue(result.err().contains("corpus directory does not exist"));
    }

    @Test
    void verifiesExistingEntriesBeforeGenerating(@TempDir Path directory) throws Exception {
        var corpus = initializeSmallCorpus(directory, 3, 16);
        var inputDirectory = corpus.resolve(CorpusPath.INPUT.relativePath());
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
        assertEquals("", result.err());
        assertTrue(result.out().startsWith(
                "Random seed: " + Long.MAX_VALUE + System.lineSeparator()));
        assertTrue(result.out().lines()
                .anyMatch(line -> line.matches("\\[\\s+0 generated inputs\\s+]")));
        assertTrue(result.out().lines()
                .anyMatch(line -> line.contains("total elapsed")));
    }

    @Test
    void generatesANonnegativeSeedWhenNoneIsGiven(@TempDir Path directory) throws Exception {
        var corpus = initializeSmallCorpus(directory, 0, 0);

        var result = execute(
                "run", "--how=pbt", "--corpus=" + corpus, "--max-cpus=1");

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertTrue(result.out().lines().findFirst().orElseThrow().matches("Random seed: \\d+"));
        assertFalse(result.out().contains("random seed"));
    }

    @Test
    void printsPrintHelp() {
        var result = execute("print", "--help");

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertTrue(result.out().contains("Usage: fuzztla print"));
        assertTrue(result.out().contains("--corpus=DIR"));
        assertTrue(result.out().contains("--envelope"));
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
        var envelope = execute("print", "--envelope", input.toString());

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertEquals("FALSE" + System.lineSeparator(), result.out());
        assertEquals("", result.err());
        assertEquals(CommandLine.ExitCode.OK, envelope.exitCode());
        assertEquals(
                String.join(
                        System.lineSeparator(), "kind: expr", "input:", "  FALSE", ""),
                envelope.out());
        assertEquals("", envelope.err());
    }

    @Test
    void printsEnvelopeStageMetadataWithElapsedDuration(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("metadata.cbor");
        var startTime = Instant.parse("2026-08-13T14:26:07Z");
        var endTime = startTime.plusSeconds(93_784);
        var encoded = CorpusEnvelopeCodec.withStageMetadata(
                CorpusInputCodec.encode(CorpusInput.expression(new byte[0])),
                new StageMetadata("parser", "pass", startTime, endTime));
        Files.write(input, encoded);

        var result = execute("print", "--envelope", input.toString());

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertEquals(
                String.join(
                        System.lineSeparator(),
                        "kind: expr",
                        "stages:",
                        "  parser:",
                        "    verdict: pass",
                        "    startTime: 2026-08-13T14:26:07Z",
                        "    endTime: 2026-08-14T16:29:11Z (duration: 1d 2h 3m 4s)",
                        "input:",
                        "  FALSE",
                        ""),
                result.out());
        assertEquals("", result.err());
    }

    @Test
    void printsCheckerFailureCodeAndDetail(@TempDir Path directory) throws Exception {
        var input = directory.resolve("metadata.cbor");
        var startTime = Instant.parse("2026-08-13T14:26:07Z");
        var encoded = CorpusEnvelopeCodec.withStageMetadata(
                CorpusInputCodec.encode(CorpusInput.expression(new byte[0])),
                new StageMetadata(
                        "tlc",
                        "fail",
                        startTime,
                        startTime.plusSeconds(2),
                        Optional.of(new CheckerFailure(
                                CheckerFailureCode.SPEC_EVAL,
                                Optional.of("Attempted to apply Head to the empty sequence.")))));
        Files.write(input, encoded);

        var result = execute("print", "--envelope", input.toString());

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertTrue(result.out().contains("    code: 75 (spec_eval)"));
        assertTrue(result.out().contains(
                "    detail: Attempted to apply Head to the empty sequence."));
        assertEquals("", result.err());
    }

    @Test
    void printsCompactGenerationMetadataInTheEnvelope(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("metadata.cbor");
        Files.write(
                input,
                CorpusInputCodec.encode(
                        CorpusInput.expression(new byte[0]),
                        new GenerationMetadata(6, 12.5)));

        var result = execute("print", "--envelope", input.toString());

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertEquals(
                String.join(
                        System.lineSeparator(),
                        "kind: expr",
                        "gen:",
                        "  cohort: 6",
                        "  richness: 12.5",
                        "input:",
                        "  FALSE",
                        ""),
                result.out());
    }

    @Test
    void printsZeroEnvelopeDuration(@TempDir Path directory) throws Exception {
        var input = directory.resolve("metadata.cbor");
        var timestamp = Instant.parse("2026-08-13T14:26:07Z");
        var encoded = CorpusEnvelopeCodec.withStageMetadata(
                CorpusInputCodec.encode(CorpusInput.expression(new byte[0])),
                new StageMetadata("parser", "fail", timestamp, timestamp));
        Files.write(input, encoded);

        var result = execute("print", "--envelope", input.toString());

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertTrue(result.out().contains(
                "endTime: 2026-08-13T14:26:07Z (duration: 0s)"));
        assertEquals("", result.err());
    }

    @Test
    void indentsEveryLineOfMultilineEnvelopeInput(@TempDir Path directory) throws Exception {
        var input = directory.resolve("multiline.cbor");
        var generatorInput = Base64.getDecoder()
                .decode("LNehJNvsP7MYvY+AwsbJ/oNuCdm3JRQxvq0=");
        Files.write(
                input, CorpusInputCodec.encode(CorpusInput.expression(generatorInput)));

        var result = execute("print", "--envelope", input.toString());

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        var marker = "input:" + System.lineSeparator();
        var renderedInput = result.out().substring(result.out().indexOf(marker) + marker.length());
        assertTrue(renderedInput.lines().count() > 1);
        assertTrue(renderedInput.lines().allMatch(line -> line.startsWith("  ")));
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
        assertTrue(result.out().contains("@type: Bool;"), result.out());
        assertTrue(result.out().contains("VARIABLE"));
        assertTrue(result.out().contains("exprValue"));
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
        var rawEnvelopeResult = execute("print", "--envelope", raw.toString());
        var moduleEnvelopeResult = execute("print", "--envelope", module.toString());

        assertEquals(CommandLine.ExitCode.SOFTWARE, rawResult.exitCode());
        assertTrue(rawResult.err().contains("cannot decode"));
        assertEquals(CommandLine.ExitCode.SOFTWARE, moduleResult.exitCode());
        assertTrue(moduleResult.err().contains("unsupported input kind 'module'"));
        assertEquals(CommandLine.ExitCode.SOFTWARE, rawEnvelopeResult.exitCode());
        assertTrue(rawEnvelopeResult.err().contains("cannot decode"));
        assertEquals(CommandLine.ExitCode.SOFTWARE, moduleEnvelopeResult.exitCode());
        assertTrue(moduleEnvelopeResult.err().contains("unsupported input kind 'module'"));
    }

    @Test
    void printsUsingCorpusGeneratorConfiguration(@TempDir Path directory) throws Exception {
        var corpus = initializeSmallCorpus(directory, 0, 0);
        var input = directory.resolve("empty.cbor");
        Files.write(input, CorpusInputCodec.encode(CorpusInput.expression(new byte[0])));

        var result = execute("print", "--corpus=" + corpus, input.toString());
        var envelope = execute(
                "print", "--envelope", "--corpus=" + corpus, input.toString());

        assertEquals(CommandLine.ExitCode.OK, result.exitCode());
        assertEquals("FALSE" + System.lineSeparator(), result.out());
        assertEquals("", result.err());
        assertEquals(CommandLine.ExitCode.OK, envelope.exitCode());
        assertTrue(envelope.out().endsWith(
                "input:" + System.lineSeparator() + "  FALSE" + System.lineSeparator()));
        assertEquals("", envelope.err());
    }

    @Test
    void rejectsCombiningSpecificationAndEnvelopeOutput(@TempDir Path directory)
            throws Exception {
        var input = directory.resolve("input.cbor");
        Files.write(input, CorpusInputCodec.encode(CorpusInput.expression(new byte[0])));

        var result = execute("print", "--spec", "--envelope", input.toString());

        assertEquals(CommandLine.ExitCode.USAGE, result.exitCode());
        assertEquals("", result.out());
        assertTrue(result.err().contains("mutually exclusive"));
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
                        new ParserStageConfig(entries, 10),
                        Map.of(
                                CorpusStage.TLC,
                                new CheckerStageConfig(entries, 10, 512, 1),
                                CorpusStage.APALACHE,
                                new CheckerStageConfig(entries, 10, 512, 1))),
                new PbtConfig(maximumInputBytes, 10, 2.0, 1.5));
        Files.writeString(
                corpus.resolve(CorpusPath.CONFIG.relativePath()),
                TomlConfig.render(config),
                StandardCharsets.UTF_8);
        return corpus;
    }

    private String hash(byte[] input) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
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
