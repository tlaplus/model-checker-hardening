package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        assertEquals("00-inputs", corpus.inputDirectory().getFileName().toString());
        assertEquals(CorpusDirectory.StoreResult.ADDED, corpus.store(input));
        assertEquals(CorpusDirectory.StoreResult.DUPLICATE, corpus.store(input));

        var path = corpus.inputDirectory().resolve(hash(input) + ".cbor");
        var encoded = Files.readAllBytes(path);
        assertEquals(CorpusInput.expression(input), CorpusInputCodec.decode(encoded));
        assertEquals(1, corpus.verify(ACCEPT));
    }

    @Test
    void duplicateDetectionIgnoresAdditionalMetadata(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {1, 2, 3};
        corpus.store(input);
        var path = corpus.inputDirectory().resolve(hash(input) + ".cbor");
        Files.write(path, encodeWithStageMetadata(input));

        assertEquals(CorpusDirectory.StoreResult.DUPLICATE, corpus.store(input));
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
        corpus.store(new byte[] {1});
        Generator<Void> broken = draw -> {
            throw new IllegalStateException("generator defect");
        };

        var failure = assertThrows(IllegalStateException.class, () -> corpus.verify(broken));

        assertEquals("generator defect", failure.getMessage());
    }

    @Test
    void recordsMetadataAndMovesParserEntriesAtomically(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var input = new byte[] {3, 1, 4};
        corpus.store(input);
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
        var inventory = corpus.inventory(ACCEPT);
        assertEquals(0, inventory.inputEntries());
        assertEquals(1, inventory.parserPassEntries());
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
    void rejectsConcurrentWorkflowLocks(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));

        try (var ignored = corpus.acquireLock()) {
            assertThrows(CorpusException.class, corpus::acquireLock);
        }
        try (var ignored = corpus.acquireLock()) {
            assertTrue(true);
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
