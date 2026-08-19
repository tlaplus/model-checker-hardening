package io.github.tlaplus.hardening.workflow.input;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusInputCodec;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.corpus.CorpusRunStatistics;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.StageEnvironment;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.execution.WorkflowMetrics;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;

import static org.junit.jupiter.api.Assertions.*;

class PbtStageTest {
    private static final TlaEx EMPTY = expression(0);
    private static final TlaEx RICH = expression(32);
    private static final Generator<TlaEx> ACCEPT = _ -> RICH;

    @Test
    void fillsTheGlobalTargetWithOneWorker(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var active = new AtomicInteger();
        var maximumActive = new AtomicInteger();
        Generator<TlaEx> observed = draw -> {
            var current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            active.decrementAndGet();
            return RICH;
        };

        var summary = runStage(corpus, config(16), observed, 20, 42);

        assertEquals(20, summary.generated());
        assertEquals(1, maximumActive.get());
        assertEquals(32.0, summary.minimumRichness());
        assertEquals(32.0, summary.maximumRichness());
        assertEquals(32.0, summary.averageRichness());
        assertEquals(20, corpus.recoverAndValidate(observed).inputEntries());
    }

    @Test
    void reproducesTheSameCorpusFromTheSameSeed(@TempDir Path directory) throws Exception {
        var first = CorpusDirectory.initialize(directory.resolve("first"));
        var second = CorpusDirectory.initialize(directory.resolve("second"));

        runStage(first, config(32), ACCEPT, 30, 123456789L);
        runStage(second, config(32), ACCEPT, 30, 123456789L);

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
        Generator<TlaEx> rejectFirstThree = _ -> {
            if (calls.getAndIncrement() < 3) {
                throw new InputRejectedException("retry");
            }
            return RICH;
        };

        var summary = runStage(corpus, config(8), rejectFirstThree, 4, 7);

        assertEquals(3, summary.rejected());
        assertEquals(4, summary.generated());
        assertEquals(7 + summary.duplicates(), summary.attempts());
    }

    @Test
    void stopsAtTheDerivedAttemptLimitAndPreservesProgress(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var calls = new AtomicInteger();
        Generator<TlaEx> acceptOnce = _ -> {
            if (calls.getAndIncrement() > 0) {
                throw new InputRejectedException("retry forever");
            }
            return RICH;
        };
        var queue = new WorkQueue<Path>();
        var control = new WorkflowControl(queue);
        var stage = new PbtStage(
                config(1),
                2,
                0,
                new StageEnvironment(corpus, acceptOnce, new CpuBudget(1), control),
                99,
                1,
                queue,
                new Semaphore(2),
                metrics(0));

        stage.start();
        stage.await();

        assertTrue(control.hasFailed());
        assertEquals(10_001, stage.summary().attempts());
        assertEquals(1, stage.summary().generated());
        assertEquals(10_000, stage.summary().rejected());
        assertTrue(control.failure().getMessage().contains("richness cohort 0"));
        assertTrue(control.failure().getMessage().contains("within 10000 attempts"));
        assertTrue(control.failure().getMessage().contains("best richness was 0.0"));
        assertEquals(1, corpus.recoverAndValidate(ACCEPT).inputEntries());
    }

    @Test
    void preservesTheCandidateAndStackTraceWhenTheGeneratorCrashes(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        Generator<TlaEx> overflow = _ -> {
            throw new StackOverflowError("deliberate overflow");
        };
        var queue = new WorkQueue<Path>();
        var control = new WorkflowControl(queue);
        var stage = new PbtStage(
                config(8),
                1,
                0,
                new StageEnvironment(corpus, overflow, new CpuBudget(1), control),
                42,
                1,
                queue,
                new Semaphore(1),
                metrics(0));

        stage.start();
        stage.await();

        assertTrue(control.hasFailed());
        assertInstanceOf(WorkflowException.class, control.failure());
        assertEquals(1, stage.summary().attempts());
        try (var paths = Files.list(corpus.resolve(CorpusPath.GENERATOR_CRASH))) {
            var files = paths.toList();
            var candidate = files.stream()
                    .filter(path -> path.getFileName().toString().endsWith(".cbor"))
                    .findFirst()
                    .orElseThrow();
            var report = files.stream()
                    .filter(path -> path.getFileName().toString().endsWith(".stacktrace"))
                    .findFirst()
                    .orElseThrow();
            var saved = CorpusInputCodec.decode(Files.readAllBytes(candidate));
            assertEquals(CorpusInput.Kind.EXPRESSION, saved.kind());
            assertTrue(control.failure().getMessage().contains(candidate.toString()));
            assertTrue(Files.readString(report)
                    .contains("StackOverflowError: deliberate overflow"));
        }
        assertEquals(0, corpus.recoverAndValidate(ACCEPT).totalEntries());
    }

    @Test
    void keepsTheSelectedCohortAcrossRichnessRejectionsAndRecordsAdmissionMetadata(
            @TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var seed = seedSelectingNonzeroCohort();
        var expectedCohort = firstCohort(seed, 10);
        var calls = new AtomicInteger();
        Generator<TlaEx> sparseThenRich = _ -> calls.getAndIncrement() == 0 ? EMPTY : RICH;

        var summary = runStage(
                corpus, new PbtConfig(16, 10, 2.0, 1.5), sparseThenRich, 1, seed);

        assertEquals(1, summary.richnessRejected());
        assertEquals(2, summary.attempts());
        var entry = readEntries(corpus).values().iterator().next();
        var generation = CorpusInputCodec.decodeEnvelope(entry).generation().orElseThrow();
        assertEquals(expectedCohort, generation.cohort());
        assertEquals(CollectionRichness.score(RICH, 2.0), generation.richness());
    }

    @Test
    void selectsCohortsUniformlyFromTheSeededRandomStream(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var target = 100;
        var seed = 12345L;

        runStage(corpus, new PbtConfig(32, 10, 2.0, 1.5), ACCEPT, target, seed);

        var expected = new HashMap<Integer, Integer>();
        var root = new SplittableRandom(PbtStage.workerSeeds(seed, 1)[0]);
        var cohortRandom = root.split();
        for (var index = 0; index < target; index++) {
            expected.merge(cohortRandom.nextInt(10), 1, Integer::sum);
        }
        var actual = new HashMap<Integer, Integer>();
        for (var encoded : readEntries(corpus).values()) {
            var cohort = CorpusInputCodec.decodeEnvelope(encoded)
                    .generation()
                    .orElseThrow()
                    .cohort();
            actual.merge(cohort, 1, Integer::sum);
        }
        assertEquals(expected, actual);
    }

    @Test
    void runsMultipleWorkersWithinTheSharedCpuBudget(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var active = new AtomicInteger();
        var maximumActive = new AtomicInteger();
        var firstTwoWorkers = new CountDownLatch(2);
        var release = new CountDownLatch(1);
        Generator<TlaEx> observed = _ -> {
            var current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            firstTwoWorkers.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("generator workers did not overlap");
                }
                return RICH;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            } finally {
                active.decrementAndGet();
            }
        };
        var queue = new WorkQueue<Path>();
        var control = new WorkflowControl(queue);
        var target = 12;
        var stage = new PbtStage(
                config(32),
                target,
                0,
                new StageEnvironment(corpus, observed, new CpuBudget(2), control),
                42,
                4,
                queue,
                new Semaphore(target),
                metrics(0));

        stage.start();
        try {
            assertTrue(firstTwoWorkers.await(1, TimeUnit.SECONDS));
        } finally {
            release.countDown();
        }
        stage.await();

        assertFalse(control.hasFailed());
        assertEquals(2, maximumActive.get());
        assertEquals(target, stage.summary().generated());
        assertEquals(target, corpus.recoverAndValidate(observed).inputEntries());
        var queued = 0;
        while (queue.take() != null) {
            queued++;
        }
        assertEquals(target, queued);
    }

    @Test
    void stopsPeerWorkersWhenOneWorkerCrashes(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var allWorkersEntered = new CountDownLatch(4);
        Generator<TlaEx> oneWorkerCrashes = _ -> {
            allWorkersEntered.countDown();
            try {
                if (!allWorkersEntered.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("generator workers did not start");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            if (Thread.currentThread().getName().equals("fuzztla-pbt-0")) {
                throw new StackOverflowError("worker-local failure");
            }
            return RICH;
        };
        var queue = new WorkQueue<Path>();
        var control = new WorkflowControl(queue);
        var stage = new PbtStage(
                config(32),
                20,
                0,
                new StageEnvironment(corpus, oneWorkerCrashes, new CpuBudget(4), control),
                42,
                4,
                queue,
                new Semaphore(20),
                metrics(0));

        stage.start();
        stage.await();

        assertTrue(control.hasFailed());
        assertInstanceOf(WorkflowException.class, control.failure());
        assertTrue(control.failure().getMessage().contains("input generator worker 0"));
        assertTrue(control.failure().getMessage().contains("worker-local failure"));
        while (queue.take() != null) {
            // Drain entries admitted before the failure and observe the closed queue.
        }
    }

    @Test
    void derivesStableDistinctWorkerSeeds() {
        var first = PbtStage.workerSeeds(1234, 8);
        var second = PbtStage.workerSeeds(1234, 8);

        assertArrayEquals(first, second);
        assertEquals(first.length, Arrays.stream(first).distinct().count());
        assertThrows(IllegalArgumentException.class, () -> PbtStage.workerSeeds(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> PbtStage.workerSeeds(1, -1));
    }

    private PbtStageSummary runStage(
            CorpusDirectory corpus,
            PbtConfig config,
            Generator<TlaEx> generator,
            int target,
            long seed)
            throws Exception {
        var queue = new WorkQueue<Path>();
        var control = new WorkflowControl(queue);
        var stage = new PbtStage(
                config,
                target,
                0,
                new StageEnvironment(corpus, generator, new CpuBudget(1), control),
                seed,
                1,
                queue,
                new Semaphore(target),
                metrics(0));
        stage.start();
        stage.await();
        assertFalse(control.hasFailed());
        return stage.summary();
    }

    private static PbtConfig config(int maximumInputBytes) {
        return new PbtConfig(maximumInputBytes, 1, 2.0, 1.5);
    }

    private static WorkflowMetrics metrics(long initialEntries) {
        return new WorkflowMetrics(CorpusRunStatistics.empty(), initialEntries);
    }

    private static long seedSelectingNonzeroCohort() {
        for (long seed = 0; ; seed++) {
            if (firstCohort(seed, 10) > 0) {
                return seed;
            }
        }
    }

    private static int firstCohort(long seed, int cohorts) {
        var workerSeed = PbtStage.workerSeeds(seed, 1)[0];
        return new SplittableRandom(workerSeed).split().nextInt(cohorts);
    }

    private static TlaEx expression(int sequenceSize) {
        var builder = new TlaTypedScopeUncheckedBuilder();
        if (sequenceSize == 0) {
            return builder.bool(false);
        }
        var elements = new TlaEx[sequenceSize];
        for (var index = 0; index < sequenceSize; index++) {
            elements[index] = builder.integer(index);
        }
        return builder.seq(elements);
    }

    private Map<String, byte[]> readEntries(CorpusDirectory corpus) throws Exception {
        try (var paths = Files.list(corpus.resolve(CorpusPath.INPUT))) {
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
