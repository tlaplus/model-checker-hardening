package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.CheckerFailure;
import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusDirectoryTest {
    private static final Generator<Void> ACCEPT = draw -> null;

    @Test
    void resolvesTheCompleteTypedCorpusLayout(@TempDir Path directory) throws Exception {
        var root = directory.resolve("corpus").toAbsolutePath().normalize();
        var corpus = CorpusDirectory.initialize(root);
        var relativePaths = Map.ofEntries(
                Map.entry(CorpusPath.ROOT, Path.of("")),
                Map.entry(CorpusPath.CONFIG, Path.of("config.toml")),
                Map.entry(CorpusPath.INPUT, Path.of("00-inputs")),
                Map.entry(CorpusPath.PARSER_PASS, Path.of("01parser-pass")),
                Map.entry(CorpusPath.PARSER_FAIL, Path.of("01parser-fail")),
                Map.entry(CorpusPath.PARSER_CRASH, Path.of("01parser-crash")),
                Map.entry(CorpusPath.TLC_INPUT, Path.of("02tlc-inputs")),
                Map.entry(CorpusPath.TLC_PASS, Path.of("02tlc-pass")),
                Map.entry(CorpusPath.TLC_FAIL, Path.of("02tlc-fail")),
                Map.entry(CorpusPath.TLC_CRASH, Path.of("02tlc-crash")),
                Map.entry(CorpusPath.APALACHE_INPUT, Path.of("02apa-inputs")),
                Map.entry(CorpusPath.WORK, Path.of(".work")),
                Map.entry(
                        CorpusPath.GENERATOR_CRASH,
                        Path.of(".work", "generator-crash")),
                Map.entry(CorpusPath.PARSER_SCRATCH, Path.of(".work", "parser-tmp")),
                Map.entry(CorpusPath.TLC_SCRATCH, Path.of(".work", "tlc-tmp")),
                Map.entry(CorpusPath.LOCK, Path.of(".workflow.lock")));

        assertEquals(Set.of(CorpusPath.values()), relativePaths.keySet());
        assertEquals(
                relativePaths.size(),
                relativePaths.values().stream().distinct().count());
        for (var entry : relativePaths.entrySet()) {
            var corpusPath = entry.getKey();
            assertEquals(entry.getValue(), corpusPath.relativePath());
            assertEquals(root.resolve(entry.getValue()).normalize(), corpus.resolve(corpusPath));
            if (corpusPath.isRequired()) {
                assertTrue(corpusPath.isDirectory()
                        ? Files.isDirectory(corpus.resolve(corpusPath))
                        : Files.isRegularFile(corpus.resolve(corpusPath)));
            } else {
                assertFalse(Files.exists(corpus.resolve(corpusPath)));
            }
        }
    }

    @Test
    void refusesIncorrectCorpusAndInputPathTypes(@TempDir Path directory) throws Exception {
        var rootFile = directory.resolve("root-file");
        Files.createFile(rootFile);
        assertThrows(CorpusException.class, () -> CorpusDirectory.initialize(rootFile));

        var corpus = directory.resolve("corpus");
        Files.createDirectories(corpus);
        Files.createFile(corpus.resolve(CorpusPath.INPUT.relativePath()));
        var failure =
                assertThrows(CorpusException.class, () -> CorpusDirectory.initialize(corpus));
        assertTrue(failure.getMessage().contains("workflow path is not a directory"));
    }

    @Test
    void storesCborUnderThePayloadsLowercaseDigest(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {0, 1, (byte) 0xff};
        var generation = new GenerationMetadata(3, 5.0);

        assertEquals("00-inputs", corpus.resolve(CorpusPath.INPUT).getFileName().toString());
        assertEquals(StoreResult.ADDED, corpus.store(input, generation));
        assertEquals(StoreResult.DUPLICATE, corpus.store(input, generation));

        var path = corpus.resolve(CorpusPath.INPUT).resolve(hash(input) + ".cbor");
        var encoded = Files.readAllBytes(path);
        assertEquals(CorpusInput.expression(input), CorpusInputCodec.decode(encoded));
        assertEquals(
                generation,
                CorpusInputCodec.decodeEnvelope(encoded).generation().orElseThrow());
        assertEquals(1, corpus.recoverAndValidate(ACCEPT).totalEntries());
    }

    @Test
    void duplicateDetectionIgnoresAdditionalMetadata(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {1, 2, 3};
        corpus.store(input);
        var path = corpus.resolve(CorpusPath.INPUT).resolve(hash(input) + ".cbor");
        Files.write(path, encodeWithStageMetadata(input));

        assertEquals(StoreResult.DUPLICATE, corpus.store(input));
        assertEquals(1, corpus.recoverAndValidate(ACCEPT).totalEntries());
    }

    @Test
    void rejectsNonCborEntryNamesBeforeGeneration(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {1};
        Files.write(corpus.resolve(CorpusPath.INPUT).resolve(hash(input) + ".expr"), input);

        var failure = assertThrows(
                CorpusException.class, () -> corpus.recoverAndValidate(ACCEPT));

        assertTrue(failure.getMessage().contains("invalid corpus entry name"));
    }

    @Test
    void rejectsEntriesWhoseDigestDoesNotMatch(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var original = new byte[] {1, 2, 3};
        corpus.store(original);
        var path = corpus.resolve(CorpusPath.INPUT).resolve(hash(original) + ".cbor");
        Files.write(path, CorpusInputCodec.encode(CorpusInput.expression(new byte[] {4, 5, 6})));

        var failure = assertThrows(
                CorpusException.class, () -> corpus.recoverAndValidate(ACCEPT));

        assertTrue(failure.getMessage().contains("hash does not match"));
    }

    @Test
    void rejectsMalformedCborAndModuleInputs(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var malformed = new byte[] {1};
        var malformedPath = corpus.resolve(CorpusPath.INPUT).resolve(hash(malformed) + ".cbor");
        Files.write(malformedPath, malformed);

        var malformedFailure = assertThrows(
                CorpusException.class, () -> corpus.recoverAndValidate(ACCEPT));
        assertTrue(malformedFailure.getMessage().contains("invalid CBOR corpus entry"));

        Files.delete(malformedPath);
        var moduleInput = new byte[] {7};
        var modulePath = corpus.resolve(CorpusPath.INPUT).resolve(hash(moduleInput) + ".cbor");
        Files.write(
                modulePath,
                CorpusInputCodec.encode(
                        new CorpusInput(CorpusInput.Kind.MODULE, moduleInput)));

        var moduleFailure = assertThrows(
                CorpusException.class, () -> corpus.recoverAndValidate(ACCEPT));
        assertTrue(moduleFailure.getMessage().contains("unsupported corpus input kind 'module'"));
    }

    @Test
    void rejectsEntriesThatTheGeneratorRejects(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        corpus.store(new byte[] {1});
        Generator<Void> reject = draw -> {
            throw new InputRejectedException("not applicable");
        };

        var failure = assertThrows(
                CorpusException.class, () -> corpus.recoverAndValidate(reject));

        assertTrue(failure.getMessage().contains("corpus entry is rejected"));
        assertTrue(failure.getMessage().contains("not applicable"));
    }

    @Test
    void propagatesUnexpectedGeneratorFailures(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {1};
        corpus.store(input);
        Generator<Void> broken = draw -> {
            throw new IllegalStateException("generator defect");
        };

        var failure = assertThrows(
                CorpusException.class, () -> corpus.recoverAndValidate(broken));

        var source = corpus.inputPath(input);
        var candidate = corpus.resolve(CorpusPath.GENERATOR_CRASH).resolve(hash(input) + ".cbor");
        var report = corpus.resolve(CorpusPath.GENERATOR_CRASH)
                .resolve(hash(input) + CorpusDirectory.CRASH_REPORT_EXTENSION);
        assertTrue(failure.getMessage().contains(source.toString()));
        assertTrue(failure.getMessage().contains(candidate.toString()));
        assertTrue(failure.getCause() instanceof IllegalStateException);
        assertEquals(
                CorpusInput.expression(input),
                CorpusInputCodec.decode(Files.readAllBytes(candidate)));
        assertTrue(Files.readString(report).contains("IllegalStateException: generator defect"));
        assertEquals(1, corpus.recoverAndValidate(ACCEPT).totalEntries());
    }

    @Test
    void storesGeneratorCrashDiagnosticsOutsideTheCorpusInventory(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {3, 1, 4};

        var candidate =
                corpus.recordGeneratorCrash(input, new StackOverflowError("deliberate overflow"));

        var report = candidate.resolveSibling(
                hash(input) + CorpusDirectory.CRASH_REPORT_EXTENSION);
        assertEquals(
                corpus.resolve(CorpusPath.GENERATOR_CRASH).resolve(hash(input) + ".cbor"),
                candidate);
        assertEquals(
                CorpusInput.expression(input),
                CorpusInputCodec.decode(Files.readAllBytes(candidate)));
        assertTrue(Files.readString(report).contains("StackOverflowError: deliberate overflow"));
        assertEquals(0, corpus.recoverAndValidate(ACCEPT).totalEntries());
    }

    @Test
    void recordsMetadataAndMovesParserEntriesAtomically(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {3, 1, 4};
        var generation = new GenerationMetadata(2, 3.0);
        corpus.store(input, generation);
        var source = corpus.inputPath(input);
        var start = Instant.ofEpochSecond(10);
        var end = Instant.ofEpochSecond(12);

        var destination = corpus.completeParser(source, "pass", start, end);

        assertEquals(corpus.resolve(CorpusPath.PARSER_PASS).resolve(source.getFileName()), destination);
        assertTrue(Files.notExists(source));
        assertEquals(
                "pass",
                CorpusInputCodec.stageVerdict(Files.readAllBytes(destination), "parser")
                        .orElseThrow());
        assertEquals(
                generation,
                CorpusInputCodec.decodeEnvelope(Files.readAllBytes(destination))
                        .generation()
                        .orElseThrow());
        var inventory = corpus.recoverAndValidate(ACCEPT);
        assertEquals(0, inventory.inputEntries());
        assertEquals(1, inventory.parserPassEntries());
        assertTrue(Files.notExists(destination));
        assertTrue(Files.exists(
                corpus.resolve(CorpusPath.TLC_INPUT).resolve(source.getFileName())));
        assertTrue(Files.exists(
                corpus.resolve(CorpusPath.APALACHE_INPUT).resolve(source.getFileName())));
        assertEquals(1, inventory.totalEntries());
    }

    @Test
    void recordsTlcMetadataAfterParserFanOut(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {4, 2};
        corpus.store(input);
        var parserPass = corpus.completeParser(
                corpus.inputPath(input),
                "pass",
                Instant.ofEpochSecond(1),
                Instant.ofEpochSecond(2));
        var tlcInput = corpus.fanOutParserPass(parserPass);

        var result = corpus.completeTlc(
                tlcInput,
                "pass",
                Instant.ofEpochSecond(3),
                Instant.ofEpochSecond(4),
                Optional.empty(),
                "TLC output");

        assertEquals(corpus.resolve(CorpusPath.TLC_PASS).resolve(tlcInput.getFileName()), result);
        assertEquals(
                "pass",
                CorpusInputCodec.stageVerdict(Files.readAllBytes(result), "parser")
                        .orElseThrow());
        assertEquals(
                "pass",
                CorpusInputCodec.stageVerdict(Files.readAllBytes(result), "tlc")
                        .orElseThrow());
        var inventory = corpus.recoverAndValidate(ACCEPT);
        assertEquals(1, inventory.parserPassEntries());
        assertEquals(1, inventory.tlcPassEntries());
        assertEquals(1, inventory.apalacheInputEntries());
        assertEquals(1, inventory.totalEntries());
    }

    @Test
    void recordsTlcFailureClassification(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {7, 5};
        corpus.store(input);
        var parserPass = corpus.completeParser(
                corpus.inputPath(input),
                "pass",
                Instant.ofEpochSecond(1),
                Instant.ofEpochSecond(2));
        var tlcInput = corpus.fanOutParserPass(parserPass);
        var failure = new CheckerFailure(
                CheckerFailureCode.SPEC_EVAL,
                Optional.of("Attempted to apply Head to the empty sequence."));

        assertThrows(
                IllegalArgumentException.class,
                () -> corpus.completeTlc(
                        tlcInput,
                        "fail",
                        Instant.ofEpochSecond(3),
                        Instant.ofEpochSecond(4),
                        Optional.empty(),
                        "full TLC output"));
        assertTrue(Files.exists(tlcInput));

        var result = corpus.completeTlc(
                tlcInput,
                "fail",
                Instant.ofEpochSecond(3),
                Instant.ofEpochSecond(4),
                Optional.of(failure),
                "full TLC output");

        var envelope = CorpusInputCodec.decodeEnvelope(Files.readAllBytes(result));
        var metadata = envelope.stages().stream()
                .filter(stage -> "tlc".equals(stage.stage()))
                .findFirst()
                .orElseThrow();
        assertEquals(Optional.of(failure), metadata.failure());
        assertEquals(1, corpus.recoverAndValidate(ACCEPT).tlcFailEntries());
    }

    @Test
    void completesAnInterruptedParserFanOut(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {7, 3};
        corpus.store(input);
        var parserPass = corpus.completeParser(
                corpus.inputPath(input),
                "pass",
                Instant.ofEpochSecond(1),
                Instant.ofEpochSecond(2));
        var tlcInput = corpus.resolve(CorpusPath.TLC_INPUT).resolve(parserPass.getFileName());
        Files.copy(parserPass, tlcInput);

        var inventory = corpus.recoverAndValidate(ACCEPT);

        assertTrue(Files.notExists(parserPass));
        assertTrue(Files.exists(tlcInput));
        assertTrue(Files.exists(
                corpus.resolve(CorpusPath.APALACHE_INPUT).resolve(parserPass.getFileName())));
        assertEquals(1, inventory.parserPassEntries());
        assertEquals(1, inventory.tlcInputEntries());
    }

    @Test
    void recoversATlcMetadataUpdateInterruptedBeforeItsMove(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {6, 2};
        corpus.store(input);
        var parserPass = corpus.completeParser(
                corpus.inputPath(input),
                "pass",
                Instant.ofEpochSecond(1),
                Instant.ofEpochSecond(2));
        var tlcInput = corpus.fanOutParserPass(parserPass);
        Files.write(
                tlcInput,
                CorpusInputCodec.withStageMetadata(
                        Files.readAllBytes(tlcInput),
                        new StageMetadata(
                                "tlc",
                                "pass",
                                Instant.ofEpochSecond(3),
                                Instant.ofEpochSecond(4))));

        var inventory = corpus.recoverAndValidate(ACCEPT);

        assertEquals(0, inventory.tlcInputEntries());
        assertEquals(1, inventory.tlcPassEntries());
        assertTrue(Files.exists(
                corpus.resolve(CorpusPath.TLC_PASS).resolve(tlcInput.getFileName())));
    }

    @Test
    void storesTlcCrashDiagnosticsBesideTheResult(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {9, 9};
        corpus.store(input);
        var parserPass = corpus.completeParser(
                corpus.inputPath(input),
                "pass",
                Instant.ofEpochSecond(1),
                Instant.ofEpochSecond(2));
        var tlcInput = corpus.fanOutParserPass(parserPass);

        var result = corpus.completeTlc(
                tlcInput,
                "crashed",
                Instant.ofEpochSecond(3),
                Instant.ofEpochSecond(4),
                Optional.empty(),
                "java.lang.OutOfMemoryError: heap");

        var report = corpus.resolve(CorpusPath.TLC_CRASH)
                .resolve(hash(input) + CorpusDirectory.CRASH_REPORT_EXTENSION);
        assertEquals(corpus.resolve(CorpusPath.TLC_CRASH).resolve(tlcInput.getFileName()), result);
        assertEquals(
                "java.lang.OutOfMemoryError: heap" + System.lineSeparator(),
                Files.readString(report));
        assertEquals(1, corpus.recoverAndValidate(ACCEPT).tlcCrashEntries());
    }

    @Test
    void rejectsACorpusWithAMissingCheckerDirectory(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("corpus");
        CorpusDirectory.initialize(root);
        var missing = root.resolve(CorpusPath.TLC_INPUT.relativePath());
        Files.delete(missing);

        var failure =
                assertThrows(CorpusException.class, () -> CorpusDirectory.openExisting(root));

        assertTrue(failure.getMessage().contains(missing.toString()));
    }

    @Test
    void storesParserCrashStackTraceBesideTheCorpusEntry(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {2, 7, 1, 8};
        corpus.store(input);
        var source = corpus.inputPath(input);
        var diagnostic = "java.lang.IllegalStateException: parser exploded\n"
                + "\tat parser.Worker.parse(Worker.java:42)";

        var destination = corpus.completeParser(
                source,
                "crashed",
                Instant.ofEpochSecond(10),
                Instant.ofEpochSecond(11),
                diagnostic);

        var report = corpus.resolve(CorpusPath.PARSER_CRASH)
                .resolve(hash(input) + CorpusDirectory.CRASH_REPORT_EXTENSION);
        assertEquals(
                corpus.resolve(CorpusPath.PARSER_CRASH).resolve(source.getFileName()),
                destination);
        assertEquals(diagnostic + System.lineSeparator(), Files.readString(report));
        assertEquals(1, corpus.recoverAndValidate(ACCEPT).parserCrashEntries());
    }

    @Test
    void recoversAMetadataUpdateInterruptedBeforeTheDirectoryMove(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {8, 5};
        corpus.store(input);
        var source = corpus.inputPath(input);
        Files.write(
                source,
                CorpusInputCodec.withStageMetadata(
                        Files.readAllBytes(source),
                        new StageMetadata(
                                "parser",
                                "fail",
                                Instant.ofEpochSecond(1),
                                Instant.ofEpochSecond(2))));

        var inventory = corpus.recoverAndValidate(ACCEPT);

        assertEquals(0, inventory.inputEntries());
        assertEquals(1, inventory.parserFailEntries());
        assertTrue(Files.exists(
                corpus.resolve(CorpusPath.PARSER_FAIL).resolve(source.getFileName())));
    }

    @Test
    void recoversAStagedParserCrashReport(@TempDir Path directory) throws Exception {
        var root = directory.resolve("corpus");
        var corpus = CorpusDirectory.initialize(root);
        var input = new byte[] {1, 6, 1, 8};
        corpus.store(input);
        var source = corpus.inputPath(input);
        Files.write(
                source,
                CorpusInputCodec.withStageMetadata(
                        Files.readAllBytes(source),
                        new StageMetadata(
                                "parser",
                                "crashed",
                                Instant.ofEpochSecond(1),
                                Instant.ofEpochSecond(2))));
        var reportName = hash(input) + CorpusDirectory.CRASH_REPORT_EXTENSION;
        Files.writeString(
                root.resolve(".work").resolve("parser-" + reportName),
                "stack trace\n");

        var inventory = corpus.recoverAndValidate(ACCEPT);

        assertEquals(1, inventory.parserCrashEntries());
        assertTrue(Files.exists(
                corpus.resolve(CorpusPath.PARSER_CRASH).resolve(source.getFileName())));
        assertEquals(
                "stack trace\n",
                Files.readString(corpus.resolve(CorpusPath.PARSER_CRASH).resolve(reportName)));
    }

    @Test
    void rejectsConcurrentWorkflowLocks(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));

        try (var ignored = corpus.acquireExclusiveLock()) {
            assertThrows(CorpusException.class, corpus::acquireExclusiveLock);
        }
        try (var ignored = corpus.acquireExclusiveLock()) {
            assertTrue(true);
        }
    }

    @Test
    void replacesAndCleansCorpusOwnedParserScratch(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("corpus");
        var corpus = CorpusDirectory.initialize(root);
        var scratchParent = root.resolve(".work").resolve("parser-tmp");
        var staleFile = scratchParent.resolve("old-run").resolve("worker").resolve("stale");
        Files.createDirectories(staleFile.getParent());
        Files.writeString(staleFile, "stale");

        Path runDirectory;
        try (var ignored = corpus.acquireExclusiveLock();
                var scratch = corpus.createParserScratch()) {
            runDirectory = scratch.directory();
            assertTrue(Files.isDirectory(runDirectory));
            assertTrue(runDirectory.startsWith(scratchParent));
            assertFalse(Files.exists(staleFile));
            Files.writeString(runDirectory.resolve("worker-artifact"), "temporary");
        }

        assertFalse(Files.exists(scratchParent));
    }

    @Test
    void rejectsANonDirectoryParserScratchPath(@TempDir Path directory)
            throws Exception {
        var root = directory.resolve("corpus");
        var corpus = CorpusDirectory.initialize(root);
        Files.writeString(root.resolve(".work").resolve("parser-tmp"), "not a directory");

        try (var ignored = corpus.acquireExclusiveLock()) {
            var failure = assertThrows(CorpusException.class, corpus::createParserScratch);
            assertTrue(failure.getMessage().contains("parser scratch path is not a directory"));
        }
    }

    private String hash(byte[] input) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
    }

    private byte[] encodeWithStageMetadata(byte[] input) throws Exception {
        return CorpusInputCodec.withStageMetadata(
                CorpusInputCodec.encode(CorpusInput.expression(input)),
                new StageMetadata(
                        "parser",
                        "pass",
                        Instant.ofEpochSecond(1),
                        Instant.ofEpochSecond(2)));
    }
}
