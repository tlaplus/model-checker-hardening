package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PbtStageTest {
    private static final Generator<Void> ACCEPT = _ -> null;

    @Test
    void fillsTheGlobalTargetWithOneWorker(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var active = new AtomicInteger();
        var maximumActive = new AtomicInteger();
        Generator<Void> observed = draw -> {
            var current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            active.decrementAndGet();
            return null;
        };

        var summary = runStage(corpus, new PbtConfig(16), observed, 20, 42);

        assertEquals(20, summary.added());
        assertEquals(1, maximumActive.get());
        assertEquals(20, corpus.inventory(observed).inputEntries());
    }

    @Test
    void reproducesTheSameCorpusFromTheSameSeed(@TempDir Path directory) throws Exception {
        var first = CorpusDirectory.initialize(directory.resolve("first"));
        var second = CorpusDirectory.initialize(directory.resolve("second"));

        runStage(first, new PbtConfig(32), ACCEPT, 30, 123456789L);
        runStage(second, new PbtConfig(32), ACCEPT, 30, 123456789L);

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
        Generator<Void> rejectFirstThree = _ -> {
            if (calls.getAndIncrement() < 3) {
                throw new InputRejectedException("retry");
            }
            return null;
        };

        var summary = runStage(corpus, new PbtConfig(8), rejectFirstThree, 4, 7);

        assertEquals(3, summary.rejected());
        assertEquals(4, summary.added());
        assertEquals(7 + summary.duplicates(), summary.attempts());
    }

    @Test
    void stopsAtTheDerivedAttemptLimitAndPreservesProgress(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var calls = new AtomicInteger();
        Generator<Void> acceptOnce = _ -> {
            if (calls.getAndIncrement() > 0) {
                throw new InputRejectedException("retry forever");
            }
            return null;
        };
        var queue = new WorkQueue<Path>();
        var control = new WorkflowControl(queue);
        var stage = new PbtStage(
                new PbtConfig(1),
                2,
                0,
                corpus,
                acceptOnce,
                99,
                queue,
                new Semaphore(2),
                new CpuBudget(1),
                control);

        stage.start();
        stage.await();

        assertTrue(control.hasFailed());
        assertEquals(10_000, stage.summary().attempts());
        assertEquals(1, stage.summary().added());
        assertEquals(9_999, stage.summary().rejected());
        assertEquals(1, corpus.inventory(ACCEPT).inputEntries());
    }

    private PbtStageSummary runStage(
            CorpusDirectory corpus,
            PbtConfig config,
            Generator<?> generator,
            int target,
            long seed)
            throws Exception {
        var queue = new WorkQueue<Path>();
        var control = new WorkflowControl(queue);
        var stage = new PbtStage(
                config,
                target,
                0,
                corpus,
                generator,
                seed,
                queue,
                new Semaphore(target),
                new CpuBudget(1),
                control);
        stage.start();
        stage.await();
        assertTrue(!control.hasFailed());
        return stage.summary();
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
