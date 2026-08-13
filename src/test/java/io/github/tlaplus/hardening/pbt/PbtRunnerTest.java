package io.github.tlaplus.hardening.pbt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PbtRunnerTest {
    private static final Generator<Void> ACCEPT = draw -> null;

    @Test
    void fillsTheCorpusToItsTargetAndThenDoesNothing(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var runner = new PbtRunner(new PbtConfig(20, 16), ACCEPT);

        var first = runner.run(corpus, 42);
        var second = runner.run(corpus, 42);

        assertEquals(0, first.existing());
        assertEquals(20, first.added());
        assertEquals(20, corpus.verify(ACCEPT));
        assertEquals(new PbtRunSummary(42, 20, 0, 0, 0, 0), second);
    }

    @Test
    void reproducesTheSameCorpusFromTheSameSeed(@TempDir Path directory) throws Exception {
        var first = CorpusDirectory.initialize(directory.resolve("first"));
        var second = CorpusDirectory.initialize(directory.resolve("second"));
        var runner = new PbtRunner(new PbtConfig(30, 32), ACCEPT);

        runner.run(first, 123456789L);
        runner.run(second, 123456789L);

        var firstEntries = readEntries(first);
        var secondEntries = readEntries(second);
        assertEquals(firstEntries.keySet(), secondEntries.keySet());
        for (var name : firstEntries.keySet()) {
            assertArrayEquals(firstEntries.get(name), secondEntries.get(name));
        }
    }

    @Test
    void retriesExpectedInputRejections(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var calls = new AtomicInteger();
        Generator<Void> rejectFirstThree = draw -> {
            if (calls.getAndIncrement() < 3) {
                throw new InputRejectedException("retry");
            }
            return null;
        };
        var runner = new PbtRunner(new PbtConfig(4, 8), rejectFirstThree);

        var summary = runner.run(corpus, 7);

        assertEquals(3, summary.rejected());
        assertEquals(4, summary.added());
        assertEquals(7 + summary.duplicates(), summary.attempts());
    }

    @Test
    void reportsDuplicatesWhileCollectingTheFiniteOneByteSpace(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var runner = new PbtRunner(new PbtConfig(257, 1), ACCEPT);

        var summary = runner.run(corpus, 1);

        assertEquals(257, summary.added());
        assertTrue(summary.duplicates() > 0);
        assertEquals(257 + summary.duplicates(), summary.attempts());
    }

    @Test
    void stopsAfterTheDerivedAttemptLimitAndPreservesProgress(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var calls = new AtomicInteger();
        Generator<Void> acceptOnce = draw -> {
            if (calls.getAndIncrement() > 0) {
                throw new InputRejectedException("retry forever");
            }
            return null;
        };
        var runner = new PbtRunner(new PbtConfig(2, 1), acceptOnce);

        var failure = assertThrows(PbtException.class, () -> runner.run(corpus, 99));

        assertEquals(10_000, failure.summary().attempts());
        assertEquals(1, failure.summary().added());
        assertEquals(9_999, failure.summary().rejected());
        try (var entries = Files.list(corpus.inputDirectory())) {
            assertEquals(1, entries.count());
        }
        assertTrue(failure.getMessage().contains("within 10000 attempts"));
    }

    @Test
    void rejectsNegativeSeedsBeforeInspectingTheCorpus(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        Files.write(corpus.inputDirectory().resolve("invalid"), new byte[] {1});
        var runner = new PbtRunner(new PbtConfig(1, 0), ACCEPT);

        var failure = assertThrows(IllegalArgumentException.class, () -> runner.run(corpus, -1));

        assertEquals("seed must be nonnegative", failure.getMessage());
    }

    private Map<String, byte[]> readEntries(CorpusDirectory corpus) throws Exception {
        try (var paths = Files.list(corpus.inputDirectory())) {
            return paths.collect(Collectors.toMap(
                    path -> path.getFileName().toString(),
                    path -> {
                        try {
                            return Files.readAllBytes(path);
                        } catch (Exception exception) {
                            throw new AssertionError(exception);
                        }
                    }));
        }
    }
}
