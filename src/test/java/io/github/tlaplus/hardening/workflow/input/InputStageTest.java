package io.github.tlaplus.hardening.workflow.input;

import static org.junit.jupiter.api.Assertions.*;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.common.Digests;
import io.github.tlaplus.hardening.corpus.CorpusEntryValidator;
import io.github.tlaplus.hardening.corpus.CorpusEnvelope;
import io.github.tlaplus.hardening.corpus.CorpusEnvelopeCodec;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusInputCodec;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.corpus.CorpusRunStatistics;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.signature.KnownDefectDatabase;
import java.util.List;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.mutation.ByteMutator;
import io.github.tlaplus.hardening.mutation.MutationOperator;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.GeneratorSummary;
import io.github.tlaplus.hardening.workflow.execution.StageEnvironment;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.execution.GeneratorStatistics;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import io.github.tlaplus.hardening.workflow.worker.ToolWorkerProtocol;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InputStageTest {
    private static final TlaEx EMPTY = expression(0);
    private static final TlaEx RICH = expression(32);
    private static final Generator<TlaEx> ACCEPT = _ -> RICH;

    @Test
    void fillsTheGlobalTargetWithOneWorker(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
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
        assertEquals(32.0, summary.aggregate().richness().minimum());
        assertEquals(32.0, summary.aggregate().richness().maximum());
        assertEquals(32.0, summary.aggregate().richness().average());
        assertEquals(20, corpus.recoverAndValidate(CorpusEntryValidator.NONE).pendingEntries(CorpusStage.PARSER));
    }

    @Test
    void reproducesTheSameCorpusFromTheSameSeed(@TempDir Path directory) throws Exception {
        var first = CorpusDirectory.initialize(directory.resolve("first"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var second = CorpusDirectory.initialize(directory.resolve("second"), TomlConfig.render(FuzzTlaConfig.defaults()));

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
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var calls = new AtomicInteger();
        Generator<TlaEx> rejectFirstThree = _ -> {
            if (calls.getAndIncrement() < 3) {
                throw new InputRejectedException("retry");
            }
            return RICH;
        };

        var summary = runStage(corpus, config(8), rejectFirstThree, 4, 7);

        assertEquals(3, summary.aggregate().rejected());
        assertEquals(4, summary.generated());
        assertEquals(7 + summary.aggregate().duplicates(), summary.aggregate().attempts());
    }

    @Test
    void rejectsACandidateThatRendersPastTheWorkerRequestFrame(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var oversized = new TlaTypedScopeUncheckedBuilder()
                .str("x".repeat(ToolWorkerProtocol.maximumMessageBytes() / 2 + 64));
        var calls = new AtomicInteger();
        Generator<TlaEx> oversizedThenRich = _ -> calls.getAndIncrement() == 0 ? oversized : RICH;

        var summary = runStage(corpus, config(8), oversizedThenRich, 1, 7);

        assertEquals(1, summary.generated());
        assertEquals(1, summary.aggregate().rejected());
        assertEquals(2, summary.aggregate().attempts());
        assertEquals(
                1,
                corpus.recoverAndValidate(CorpusEntryValidator.NONE).pendingEntries(CorpusStage.PARSER));
    }

    @Test
    void stopsAtTheDerivedAttemptLimitAndPreservesProgress(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var calls = new AtomicInteger();
        Generator<TlaEx> acceptOnce = _ -> {
            if (calls.getAndIncrement() > 0) {
                throw new InputRejectedException("retry forever");
            }
            return RICH;
        };
        var queue = new WorkQueue<Path>();
        var control = new WorkflowControl(queue);
        var stage = new InputStage(
                InputAdmission.withoutKnownDefects(config(1)),
                plan(99, 2, 1, config(1)),
                new StageEnvironment(corpus, decoders(acceptOnce), new CpuBudget(1), control),
                new InputHandoff(queue, new Semaphore(2)),
                metrics(0));

        stage.start();
        stage.await();

        assertTrue(control.hasFailed());
        assertEquals(10_001, stage.summary().aggregate().attempts());
        assertEquals(1, stage.summary().generated());
        assertEquals(10_000, stage.summary().aggregate().rejected());
        assertTrue(control.failure().getMessage().contains("richness cohort 0"));
        assertTrue(control.failure().getMessage().contains("within 10000 attempts"));
        assertTrue(control.failure().getMessage().contains("best richness was 0.0"));
        assertEquals(1, corpus.recoverAndValidate(CorpusEntryValidator.NONE).pendingEntries(CorpusStage.PARSER));
    }

    @Test
    void preservesTheCandidateAndStackTraceWhenTheGeneratorCrashes(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        Generator<TlaEx> overflow = _ -> {
            throw new StackOverflowError("deliberate overflow");
        };
        var queue = new WorkQueue<Path>();
        var control = new WorkflowControl(queue);
        var stage = new InputStage(
                InputAdmission.withoutKnownDefects(config(8)),
                plan(42, 1, 1, config(8)),
                new StageEnvironment(corpus, decoders(overflow), new CpuBudget(1), control),
                new InputHandoff(queue, new Semaphore(1)),
                metrics(0));

        stage.start();
        stage.await();

        assertTrue(control.hasFailed());
        assertInstanceOf(WorkflowException.class, control.failure());
        assertEquals(1, stage.summary().aggregate().attempts());
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
            assertEquals(InputKind.EXPRESSION, saved.kind());
            assertTrue(control.failure().getMessage().contains(candidate.toString()));
            assertTrue(Files.readString(report)
                    .contains("StackOverflowError: deliberate overflow"));
        }
        assertEquals(0, corpus.recoverAndValidate(CorpusEntryValidator.NONE).totalEntries());
    }

    @Test
    void keepsTheSelectedCohortAcrossRichnessRejectionsAndRecordsAdmissionMetadata(
            @TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var seed = seedSelectingNonzeroCohort();
        var expectedCohort = firstCohort(seed, 10);
        var calls = new AtomicInteger();
        Generator<TlaEx> sparseThenRich = _ -> calls.getAndIncrement() == 0 ? EMPTY : RICH;

        var summary = runStage(
                corpus, new PbtConfig(16, 10, 2.0, 1.5), sparseThenRich, 1, seed);

        assertEquals(1, summary.aggregate().richnessRejected());
        assertEquals(2, summary.aggregate().attempts());
        var entry = readEntries(corpus).values().iterator().next();
        var generation = CorpusEnvelopeCodec.decodeEnvelope(entry).generation().orElseThrow();
        assertEquals(expectedCohort, generation.cohort());
        assertEquals(CollectionRichness.score(RICH, 2.0), generation.richness());
    }

    @Test
    void selectsCohortsUniformlyFromTheSeededRandomStream(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var target = 100;
        var seed = 12345L;

        runStage(corpus, new PbtConfig(32, 10, 2.0, 1.5), ACCEPT, target, seed);

        var expected = new HashMap<Integer, Integer>();
        for (var index = 0; index < target; index++) {
            expected.merge(cohort(seed, index, 10), 1, Integer::sum);
        }
        var actual = new HashMap<Integer, Integer>();
        for (var encoded : readEntries(corpus).values()) {
            var cohort = CorpusEnvelopeCodec.decodeEnvelope(encoded)
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
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
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
        var stage = new InputStage(
                InputAdmission.withoutKnownDefects(config(32)),
                plan(42, target, 4, config(32)),
                new StageEnvironment(corpus, decoders(observed), new CpuBudget(2), control),
                new InputHandoff(queue, new Semaphore(target)),
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
        assertEquals(target, corpus.recoverAndValidate(CorpusEntryValidator.NONE).pendingEntries(CorpusStage.PARSER));
        var queued = 0;
        while (queue.take() != null) {
            queued++;
        }
        assertEquals(target, queued);
    }

    @Test
    void stopsPeerWorkersWhenOneWorkerCrashes(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
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
            if (Thread.currentThread().getName().equals("fuzztla-input-0")) {
                throw new StackOverflowError("worker-local failure");
            }
            return RICH;
        };
        var queue = new WorkQueue<Path>();
        var control = new WorkflowControl(queue);
        var stage = new InputStage(
                InputAdmission.withoutKnownDefects(config(32)),
                plan(42, 20, 4, config(32)),
                new StageEnvironment(corpus, decoders(oneWorkerCrashes), new CpuBudget(4), control),
                new InputHandoff(queue, new Semaphore(20)),
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
    void quarantinesKnownDefectsUpToTheSampleCapAndCountsTheRest(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var database = Files.writeString(directory.resolve("known-defects.toml"), """
                [[signature]]
                id = "sequence-from-zero"
                references = ["none"]
                description = "A sequence literal that starts with zero."
                match = ['(TUPLE 0 ...)']
                """);
        var calls = new AtomicInteger();
        Generator<TlaEx> defectsThenClean = _ -> calls.getAndIncrement() < 5 ? RICH : EMPTY;
        var queue = new WorkQueue<Path>();
        var control = new WorkflowControl(queue);
        var stage = new InputStage(
                new InputAdmission(
                        config(16),
                        KnownDefectDatabase.load(List.of(database)),
                        KnownDefectQuarantine.open(corpus, 2)),
                plan(7, 1, 1, config(16)),
                new StageEnvironment(corpus, decoders(defectsThenClean), new CpuBudget(1), control),
                new InputHandoff(queue, new Semaphore(1)),
                metrics(0));

        stage.start();
        stage.await();

        assertFalse(control.hasFailed());
        assertEquals(1, stage.summary().generated());
        assertEquals(Map.of("sequence-from-zero", 5L), stage.summary().aggregate().knownDefects());
        assertEquals(Map.of("sequence-from-zero", 2L), corpus.knownDefectSamples());
        assertEquals(1, corpus.recoverAndValidate(CorpusEntryValidator.NONE).pendingEntries(CorpusStage.PARSER));
        try (var quarantined = Files.list(corpus.resolve(CorpusPath.KNOWN_DEFECTS))) {
            for (var path : quarantined.toList()) {
                var generation = CorpusEnvelopeCodec.decodeEnvelope(Files.readAllBytes(path))
                        .generation()
                        .orElseThrow();
                assertEquals(List.of("sequence-from-zero"), generation.knownDefects());
            }
        }
        // A later run continues the cap where this one stopped.
        assertEquals(
                KnownDefectQuarantine.Outcome.DISCARDED,
                KnownDefectQuarantine.open(corpus, 2).record(
                        InputKind.EXPRESSION,
                        new byte[] {1, 2, 3},
                        GenerationMetadata.generated(0, 0, 0.0).withKnownDefects(List.of("sequence-from-zero"))));
    }

    @Test
    void storesMutantsWithTheirParentAndOperatorsBesideGeneratedEntries(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var parent = new byte[] {1, 2, 3, 4};
        var decoder = decoders(draw -> expression(1 + draw.remaining()));
        var pool = pool(corpus, decoder, parent, 6);
        var mutants = new MutantCandidates(pool, new ByteMutator(Map.of(MutationOperator.INSERT, 1), 1, 64));

        var stage = runPlan(corpus, decoder, new GenerationPlan(InputKind.EXPRESSION, 3, 11, 0, 1, List.of(
                new GenerationPlan.Quota(mutants, 3),
                new GenerationPlan.Quota(new PbtCandidates(config(32)), 2))));

        var metadata = readEntries(corpus).values().stream()
                .map(encoded -> decode(encoded).generation().orElseThrow())
                .toList();
        assertEquals(5, metadata.size());
        assertTrue(metadata.stream().allMatch(entry -> entry.generation().equals(OptionalInt.of(3))));
        var mutated = metadata.stream().filter(entry -> entry.mutation().isPresent()).toList();
        assertEquals(3, mutated.size());
        for (var entry : mutated) {
            assertEquals(Digests.digest(parent), entry.mutation().orElseThrow().parent());
            assertEquals(List.of(MutationOperator.INSERT), entry.mutation().orElseThrow().operators());
            assertEquals(6, entry.cohort());
        }
        assertEquals(0, stage.summary().aggregate().clones());
    }

    @Test
    void rejectsMutantsThatRenderToTheirParentsModule(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        // Every input decodes to the same module, so every mutant is a clone of its parent.
        var decoder = decoders(ACCEPT);
        var pool = pool(corpus, decoder, new byte[] {9, 9}, 0);
        var mutants = new MutantCandidates(pool, new ByteMutator(Map.of(MutationOperator.BITFLIP, 1), 1, 64));
        var queue = new WorkQueue<Path>();
        var control = new WorkflowControl(queue);
        var stage = new InputStage(
                InputAdmission.withoutKnownDefects(config(8)),
                new GenerationPlan(InputKind.EXPRESSION, 1, 5, 0, 1, List.of(new GenerationPlan.Quota(mutants, 1))),
                new StageEnvironment(corpus, decoder, new CpuBudget(1), control),
                new InputHandoff(queue, new Semaphore(1)),
                metrics(0));

        stage.start();
        stage.await();

        assertTrue(control.hasFailed());
        assertEquals(10_000, stage.summary().aggregate().clones());
        assertTrue(control.failure().getMessage().contains("a mutant of a parent pool of 1 entries"),
                control.failure().getMessage());
        assertTrue(control.failure().getMessage().contains("10000 were clones of their parent"));
        assertEquals(0, stage.summary().generated());
    }

    @Test
    void readsOnlyParentsOfTheGeneratedKind(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var decoder = decoders(ACCEPT);
        pool(corpus, decoder, new byte[] {1}, 0);
        writeParent(corpus, InputKind.MODULE, new byte[] {2}, 0);

        assertEquals(1, ParentPool.load(corpus, InputKind.EXPRESSION, decoder.decoder(InputKind.EXPRESSION)).size());
        assertEquals(1, ParentPool.load(corpus, InputKind.MODULE, decoder.decoder(InputKind.MODULE)).size());
    }

    @Test
    void derivesStableDistinctGenerationSeeds() {
        var seeds = new java.util.HashSet<Long>();
        for (var generation = 0; generation < 16; generation++) {
            var seed = InputStage.generationSeed(1234, generation);
            assertEquals(seed, InputStage.generationSeed(1234, generation));
            assertTrue(seed >= 0);
            seeds.add(seed);
        }
        assertEquals(16, seeds.size());
        assertThrows(IllegalArgumentException.class, () -> InputStage.generationSeed(1, -1));
    }

    @Test
    void derivesStableDistinctTargetSeeds() {
        var seeds = new java.util.HashSet<Long>();
        for (var target = 0; target < 1000; target++) {
            var seed = InputStage.derivedSeed(1234, target);
            assertEquals(seed, InputStage.derivedSeed(1234, target));
            assertTrue(seed >= 0);
            seeds.add(seed);
        }
        assertEquals(1000, seeds.size());
        assertNotEquals(InputStage.derivedSeed(1234, 0), InputStage.derivedSeed(1235, 0));
        assertThrows(IllegalArgumentException.class, () -> InputStage.derivedSeed(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> InputStage.derivedSeed(1, -1));
    }

    @Test
    void admitsTheSameEntriesWhateverTheNumberOfWorkers(@TempDir Path directory) throws Exception {
        var single = CorpusDirectory.initialize(directory.resolve("single"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var parallel = CorpusDirectory.initialize(directory.resolve("parallel"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var pbt = new PbtConfig(32, 10, 2.0, 1.5);

        runPlan(single, decoders(ACCEPT), pbt, plan(99, 40, 1, pbt), 1);
        runPlan(parallel, decoders(ACCEPT), pbt, plan(99, 40, 4, pbt), 4);

        var singleEntries = readEntries(single);
        var parallelEntries = readEntries(parallel);
        assertEquals(singleEntries.keySet(), parallelEntries.keySet());
        for (var name : singleEntries.keySet()) {
            assertArrayEquals(singleEntries.get(name), parallelEntries.get(name));
        }
    }

    private GeneratorSummary runStage(
            CorpusDirectory corpus,
            PbtConfig config,
            Generator<TlaEx> generator,
            int target,
            long seed)
            throws Exception {
        var queue = new WorkQueue<Path>();
        var control = new WorkflowControl(queue);
        var stage = new InputStage(
                InputAdmission.withoutKnownDefects(config),
                plan(seed, target, 1, config),
                new StageEnvironment(corpus, decoders(generator), new CpuBudget(1), control),
                new InputHandoff(queue, new Semaphore(target)),
                metrics(0));
        stage.start();
        stage.await();
        assertFalse(control.hasFailed());
        return stage.summary();
    }

    private static GenerationPlan plan(long seed, long target, int workers, PbtConfig config) {
        return new GenerationPlan(InputKind.EXPRESSION, 0, seed, 0, workers,
                List.of(new GenerationPlan.Quota(new PbtCandidates(config), target)));
    }

    private InputStage runPlan(CorpusDirectory corpus, SpecDecoders decoders, GenerationPlan plan)
            throws Exception {
        return runPlan(corpus, decoders, config(32), plan, 1);
    }

    private InputStage runPlan(
            CorpusDirectory corpus, SpecDecoders decoders, PbtConfig pbt, GenerationPlan plan, int cpus)
            throws Exception {
        var queue = new WorkQueue<Path>();
        var control = new WorkflowControl(queue);
        var entries = Math.toIntExact(plan.missingEntries());
        var stage = new InputStage(
                InputAdmission.withoutKnownDefects(pbt),
                plan,
                new StageEnvironment(corpus, decoders, new CpuBudget(cpus), control),
                new InputHandoff(queue, new Semaphore(entries)),
                metrics(0));
        stage.start();
        stage.await();
        assertFalse(control.hasFailed(), () -> String.valueOf(control.failure()));
        return stage;
    }

    /** Writes one parent into {@code 04quality-pass} and loads the pool of its kind. */
    private static ParentPool pool(CorpusDirectory corpus, SpecDecoders decoders, byte[] parent, int cohort)
            throws Exception {
        writeParent(corpus, InputKind.EXPRESSION, parent, cohort);
        return ParentPool.load(corpus, InputKind.EXPRESSION, decoders.decoder(InputKind.EXPRESSION));
    }

    private static void writeParent(CorpusDirectory corpus, InputKind kind, byte[] parent, int cohort)
            throws Exception {
        Files.write(
                corpus.resolve(CorpusPath.QUALITY_PASS).resolve(Digests.digest(parent) + ".cbor"),
                CorpusInputCodec.encode(new CorpusInput(kind, parent), GenerationMetadata.generated(0, cohort, 1.0)));
    }

    private static CorpusEnvelope decode(byte[] encoded) {
        try {
            return CorpusEnvelopeCodec.decodeEnvelope(encoded);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static SpecDecoders decoders(Generator<TlaEx> expressions) {
        return SpecDecoders.of(FuzzTlaConfig.defaults().generator())
                .replacingExpressions(expressions);
    }

    private static PbtConfig config(int maximumInputBytes) {
        return new PbtConfig(maximumInputBytes, 1, 2.0, 1.5);
    }

    private static GeneratorStatistics metrics(long initialEntries) {
        return new GeneratorStatistics(CorpusRunStatistics.empty(), initialEntries);
    }

    private static long seedSelectingNonzeroCohort() {
        for (long seed = 0; ; seed++) {
            if (firstCohort(seed, 10) > 0) {
                return seed;
            }
        }
    }

    private static int firstCohort(long seed, int cohorts) {
        return cohort(seed, 0, cohorts);
    }

    /** Returns the cohort a PBT target draws: the first value of its claim stream. */
    private static int cohort(long seed, long target, int cohorts) {
        return new SplittableRandom(InputStage.derivedSeed(seed, target)).split().nextInt(cohorts);
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
