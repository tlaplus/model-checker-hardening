package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusDirectoryTest {
    private static final Generator<Void> ACCEPT = draw -> null;

    @Test
    void refusesIncorrectCorpusAndInputPathTypes(@TempDir Path directory) throws Exception {
        var rootFile = directory.resolve("root-file");
        Files.createFile(rootFile);
        assertThrows(CorpusException.class, () -> CorpusDirectory.initialize(rootFile));

        var corpus = directory.resolve("corpus");
        Files.createDirectories(corpus);
        Files.createFile(corpus.resolve(CorpusDirectory.INPUT_DIRECTORY_NAME));
        var failure =
                assertThrows(CorpusException.class, () -> CorpusDirectory.initialize(corpus));
        assertTrue(failure.getMessage().contains("workflow path is not a directory"));
    }

    @Test
    void storesCborUnderThePayloadsLowercaseDigest(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {0, 1, (byte) 0xff};
        var generation = new GenerationMetadata(3, 5.0);

        assertEquals("00-inputs", corpus.inputDirectory().getFileName().toString());
        assertEquals(StoreResult.ADDED, corpus.store(input, generation));
        assertEquals(StoreResult.DUPLICATE, corpus.store(input, generation));

        var path = corpus.inputDirectory().resolve(hash(input) + ".cbor");
        var encoded = Files.readAllBytes(path);
        assertEquals(CorpusInput.expression(input), CorpusInputCodec.decode(encoded));
        assertEquals(
                generation,
                CorpusInputCodec.decodeEnvelope(encoded).generation().orElseThrow());
        assertEquals(1, corpus.verify(ACCEPT));
    }

    @Test
    void duplicateDetectionIgnoresAdditionalMetadata(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {1, 2, 3};
        corpus.store(input);
        var path = corpus.inputDirectory().resolve(hash(input) + ".cbor");
        Files.write(path, encodeWithStageMetadata(input));

        assertEquals(StoreResult.DUPLICATE, corpus.store(input));
        assertEquals(1, corpus.verify(ACCEPT));
    }

    @Test
    void rejectsLegacyEntryNamesBeforeGeneration(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {1};
        Files.write(corpus.inputDirectory().resolve(hash(input) + ".expr"), input);

        var failure = assertThrows(CorpusException.class, () -> corpus.verify(ACCEPT));

        assertTrue(failure.getMessage().contains("invalid corpus entry name"));
    }

    @Test
    void rejectsEntriesWhoseDigestDoesNotMatch(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var original = new byte[] {1, 2, 3};
        corpus.store(original);
        var path = corpus.inputDirectory().resolve(hash(original) + ".cbor");
        Files.write(path, CorpusInputCodec.encode(CorpusInput.expression(new byte[] {4, 5, 6})));

        var failure = assertThrows(CorpusException.class, () -> corpus.verify(ACCEPT));

        assertTrue(failure.getMessage().contains("hash does not match"));
    }

    @Test
    void rejectsMalformedCborAndModuleInputs(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var malformed = new byte[] {1};
        var malformedPath = corpus.inputDirectory().resolve(hash(malformed) + ".cbor");
        Files.write(malformedPath, malformed);

        var malformedFailure = assertThrows(CorpusException.class, () -> corpus.verify(ACCEPT));
        assertTrue(malformedFailure.getMessage().contains("invalid CBOR corpus entry"));

        Files.delete(malformedPath);
        var moduleInput = new byte[] {7};
        var modulePath = corpus.inputDirectory().resolve(hash(moduleInput) + ".cbor");
        Files.write(
                modulePath,
                CorpusInputCodec.encode(
                        new CorpusInput(CorpusInput.Kind.MODULE, moduleInput)));

        var moduleFailure = assertThrows(CorpusException.class, () -> corpus.verify(ACCEPT));
        assertTrue(moduleFailure.getMessage().contains("unsupported corpus input kind 'module'"));
    }

    @Test
    void rejectsEntriesThatTheGeneratorRejects(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        corpus.store(new byte[] {1});
        Generator<Void> reject = draw -> {
            throw new InputRejectedException("not applicable");
        };

        var failure = assertThrows(CorpusException.class, () -> corpus.verify(reject));

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

        var failure = assertThrows(CorpusException.class, () -> corpus.verify(broken));

        var source = corpus.inputPath(input);
        var candidate = corpus.generatorCrashDirectory().resolve(hash(input) + ".cbor");
        var report = corpus.generatorCrashDirectory()
                .resolve(hash(input) + CorpusDirectory.GENERATOR_CRASH_REPORT_EXTENSION);
        assertTrue(failure.getMessage().contains(source.toString()));
        assertTrue(failure.getMessage().contains(candidate.toString()));
        assertTrue(failure.getCause() instanceof IllegalStateException);
        assertEquals(
                CorpusInput.expression(input),
                CorpusInputCodec.decode(Files.readAllBytes(candidate)));
        assertTrue(Files.readString(report).contains("IllegalStateException: generator defect"));
        assertEquals(1, corpus.verify(ACCEPT));
    }

    @Test
    void storesGeneratorCrashDiagnosticsOutsideTheCorpusInventory(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {3, 1, 4};

        var candidate =
                corpus.recordGeneratorCrash(input, new StackOverflowError("deliberate overflow"));

        var report = candidate.resolveSibling(
                hash(input) + CorpusDirectory.GENERATOR_CRASH_REPORT_EXTENSION);
        assertEquals(corpus.generatorCrashDirectory().resolve(hash(input) + ".cbor"), candidate);
        assertEquals(
                CorpusInput.expression(input),
                CorpusInputCodec.decode(Files.readAllBytes(candidate)));
        assertTrue(Files.readString(report).contains("StackOverflowError: deliberate overflow"));
        assertEquals(0, corpus.verify(ACCEPT));
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

        assertEquals(corpus.parserPassDirectory().resolve(source.getFileName()), destination);
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
        var inventory = corpus.inventory(ACCEPT);
        assertEquals(0, inventory.inputEntries());
        assertEquals(1, inventory.parserPassEntries());
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

        var report = corpus.parserCrashDirectory()
                .resolve(hash(input) + CorpusDirectory.PARSER_CRASH_REPORT_EXTENSION);
        assertEquals(corpus.parserCrashDirectory().resolve(source.getFileName()), destination);
        assertEquals(diagnostic + System.lineSeparator(), Files.readString(report));
        assertEquals(1, corpus.inventory(ACCEPT).parserCrashEntries());
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

        var inventory = corpus.inventory(ACCEPT);

        assertEquals(0, inventory.inputEntries());
        assertEquals(1, inventory.parserFailEntries());
        assertTrue(Files.exists(corpus.parserFailDirectory().resolve(source.getFileName())));
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
        var reportName = hash(input) + CorpusDirectory.PARSER_CRASH_REPORT_EXTENSION;
        Files.writeString(root.resolve(".work").resolve(reportName), "stack trace\n");

        var inventory = corpus.inventory(ACCEPT);

        assertEquals(1, inventory.parserCrashEntries());
        assertTrue(Files.exists(corpus.parserCrashDirectory().resolve(source.getFileName())));
        assertEquals(
                "stack trace\n",
                Files.readString(corpus.parserCrashDirectory().resolve(reportName)));
    }

    @Test
    void rejectsConcurrentWorkflowLocks(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));

        try (var ignored = corpus.acquireLock()) {
            assertThrows(CorpusException.class, corpus::acquireLock);
        }
        try (var ignored = corpus.acquireLock()) {
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
        try (var ignored = corpus.acquireLock();
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

        try (var ignored = corpus.acquireLock()) {
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
