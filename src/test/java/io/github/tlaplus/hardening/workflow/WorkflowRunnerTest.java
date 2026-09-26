package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.ParserStageConfig;
import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.config.InputStageConfig;
import io.github.tlaplus.hardening.config.MutatorConfig;
import io.github.tlaplus.hardening.config.QualityGateConfig;
import io.github.tlaplus.hardening.config.OperatorLibraryConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.config.WorkflowConfig;
import io.github.tlaplus.hardening.corpus.CheckerSet;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusException;
import io.github.tlaplus.hardening.corpus.CorpusEntryValidator;
import io.github.tlaplus.hardening.corpus.CorpusEnvelopeCodec;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.corpus.StageResult;
import io.github.tlaplus.hardening.corpus.Technique;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.mutation.MutationOperator;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowRunnerTest {
    private static final GenerationMetadata ADMITTED = GenerationMetadata.generated(0, 0, 0.0);

    /** A corpus that runs TLC alone fans out, checks and aggregates on TLC only (ADR 0016 §5). */
    @Test
    void runsOnlyTheConfiguredCheckers(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var both = config(8, 3, 8, 16);
        var tlcOnly = both.withWorkflow(both.workflow().withEnabledCheckers(CheckerSet.of(CorpusStage.TLC)));

        var summary = new WorkflowRunner(tlcOnly, Technique.PBT).run(corpus, 42, 1);

        var inventory = summary.corpus();
        assertTrue(inventory.processedEntries(CorpusStage.TLC) > 0);
        assertEquals(0, inventory.processedEntries(CorpusStage.APALACHE));
        assertEquals(
                inventory.counts(CorpusStage.TLC).processed() - inventory.counts(CorpusStage.TLC).count(CorpusVerdict.CRASH),
                inventory.processedEntries(CorpusStage.AGGREGATOR) + inventory.pendingEntries(CorpusStage.AGGREGATOR));
        assertEquals(CheckerSet.of(CorpusStage.TLC), CorpusRecords.CHECKERS.read(corpus));
        var refused = assertThrows(WorkflowException.class,
                () -> new WorkflowRunner(both, Technique.PBT).run(corpus, 42, 1));
        assertTrue(refused.getMessage().contains("runs the checkers tlc"), refused.getMessage());
    }

    @Test
    void reportsInitialAndFinalProgressSnapshots(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = config(8, 3, 8, 16);
        var observed = new CopyOnWriteArrayList<WorkflowProgress>();

        var summary = new WorkflowRunner(config, Technique.PBT).run(corpus, 42, 1, observed::add);

        assertTrue(observed.size() >= 2);
        var initial = observed.getFirst();
        assertEquals(WorkflowProgress.Phase.RUNNING, initial.phase());
        assertEquals(42, initial.generator().seed());
        assertEquals(0, initial.corpusEntries());
        assertEquals(0, initial.backlog(CorpusStage.PARSER));
        assertEquals(0, initial.backlog(CorpusStage.TLC));
        assertEquals(0, initial.backlog(CorpusStage.APALACHE));
        assertEquals(0, initial.backlog(CorpusStage.AGGREGATOR));

        var last = observed.getLast();
        assertEquals(WorkflowProgress.Phase.FINALIZING, last.phase());
        assertEquals(summary.generator(), last.generator());
        assertEquals(summary.stage(CorpusStage.PARSER), last.stage(CorpusStage.PARSER));
        assertEquals(summary.corpus().totalEntries(), last.corpusEntries());
        assertEquals(summary.corpus().pendingEntries(CorpusStage.PARSER), last.backlog(CorpusStage.PARSER));
        assertEquals(summary.corpus().pendingEntries(CorpusStage.TLC), last.backlog(CorpusStage.TLC));
        assertEquals(summary.corpus().pendingEntries(CorpusStage.APALACHE), last.backlog(CorpusStage.APALACHE));
        assertEquals(
                summary.corpus().pendingEntries(CorpusStage.AGGREGATOR),
                last.backlog(CorpusStage.AGGREGATOR));

        long generated = -1;
        long corpusEntries = -1;
        long parsed = -1;
        var totalElapsed = Duration.ZERO;
        var finalizing = false;
        for (var progress : observed) {
            if (progress.phase() == WorkflowProgress.Phase.FINALIZING) {
                finalizing = true;
            } else {
                assertFalse(finalizing, "workflow phase must not return to RUNNING");
            }
            assertTrue(progress.generator().generated() >= generated);
            assertTrue(progress.corpusEntries() >= corpusEntries);
            assertTrue(progress.stage(CorpusStage.PARSER).processed() >= parsed);
            assertTrue(progress.backlog(CorpusStage.PARSER) >= 0);
            assertTrue(progress.backlog(CorpusStage.TLC) >= 0);
            assertTrue(progress.backlog(CorpusStage.APALACHE) >= 0);
            assertTrue(progress.backlog(CorpusStage.AGGREGATOR) >= 0);
            assertTrue(progress.totalElapsed().compareTo(totalElapsed) >= 0);
            generated = progress.generator().generated();
            corpusEntries = progress.corpusEntries();
            parsed = progress.stage(CorpusStage.PARSER).processed();
            totalElapsed = progress.totalElapsed();
        }
    }

    @Test
    void savesAndRecoversCumulativeStatisticsAcrossRuns(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = config(1, 1, 1, 0);
        var expression = new TlaTypedScopeUncheckedBuilder()
                .name("missing", TlaTypes.BOOL);
        Generator<TlaEx> generator = _ -> expression;
        var input = new byte[] {1};
        corpus.store(InputKind.EXPRESSION, input, ADMITTED);
        corpus.completeParser(
                corpus.inputPath(input),
                new StageResult(CorpusVerdict.FAIL, Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)));
        var runner = runner(config, generator);

        var first = runner.run(corpus, 42, 1);
        var second = runner.run(corpus, 43, 1);
        var saved = corpus.readRunStatistics();

        assertEquals(1, first.generator().generated());
        assertEquals(1, second.generator().generated());
        assertEquals(1, first.stage(CorpusStage.PARSER).count(CorpusVerdict.FAIL));
        assertEquals(1, second.stage(CorpusStage.PARSER).count(CorpusVerdict.FAIL));
        assertEquals(Duration.ZERO, first.stage(CorpusStage.PARSER).elapsed());
        assertEquals(Duration.ZERO, second.stage(CorpusStage.PARSER).elapsed());
        assertTrue(second.totalElapsed().compareTo(first.totalElapsed()) >= 0);
        assertEquals(second.totalElapsed().toNanos(), saved.totalElapsedNanos());
        assertEquals(second.stage(CorpusStage.PARSER).elapsed().toNanos(), saved.stageElapsedNanos(CorpusStage.PARSER));
        assertTrue(Files.isRegularFile(corpus.resolve(CorpusPath.WORKFLOW_STATISTICS)));
    }

    @Test
    void savesStatisticsWhenTheRunnerIsInterrupted(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = config(1, 1, 1, 16);
        var enteredGenerator = new CountDownLatch(1);
        Generator<TlaEx> blocking = _ -> {
            enteredGenerator.countDown();
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("unreachable");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("generator interrupted", exception);
            }
        };
        var failure = new AtomicReference<Throwable>();
        var run = Thread.ofPlatform().start(() -> {
            try {
                runner(config, blocking).run(corpus, 42, 1);
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        assertTrue(enteredGenerator.await(5, TimeUnit.SECONDS));

        run.interrupt();
        run.join(5_000);

        assertFalse(run.isAlive());
        assertTrue(failure.get() instanceof WorkflowException);
        var saved = corpus.readRunStatistics();
        assertEquals(1, saved.generator().attempts());
        assertTrue(saved.generatorElapsedNanos() > 0);
        assertTrue(saved.totalElapsedNanos() > 0);
    }

    @Test
    void failsTheWorkflowWhenExitStatisticsCannotBeSaved(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = config(0, 0, 0, 0);
        var statisticsPath = corpus.resolve(CorpusPath.WORKFLOW_STATISTICS);

        var failure = assertThrows(
                IOException.class,
                () -> new WorkflowRunner(config, Technique.PBT).run(corpus, 42, 1, _ -> {
                    try {
                        Files.createDirectory(statisticsPath);
                    } catch (java.nio.file.FileAlreadyExistsException ignored) {
                        // The initial progress callback already installed the obstruction.
                    } catch (IOException exception) {
                        throw new AssertionError(exception);
                    }
                }));

        assertTrue(failure.getMessage().contains(".workflow-stats.cbor"));
        assertTrue(Files.isDirectory(statisticsPath));
    }

    @Test
    void runsGenerationAndParsingToQuiescenceWithInputBackpressure(
            @TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = config(12, 3, 12, 16);

        var summary = new WorkflowRunner(config, Technique.PBT)
                .run(corpus, 42, Math.min(2, Runtime.getRuntime().availableProcessors()));

        assertEquals(WorkflowRunSummary.StopReason.COMPLETED, summary.stopReason());
        assertEquals(12, summary.generator().generated());
        assertEquals(12, summary.stage(CorpusStage.PARSER).processed());
        assertEquals(12, summary.corpus().totalEntries());
        assertEquals(0, summary.corpus().pendingEntries(CorpusStage.PARSER));
        assertFalse(Files.exists(corpus.resolve(CorpusPath.PARSER_SCRATCH)));
        assertFalse(Files.exists(corpus.resolve(CorpusPath.TLC_SCRATCH)));
        assertFalse(Files.exists(corpus.resolve(CorpusPath.APALACHE_SCRATCH)));
    }

    @Test
    void mutatesTheSelectedEntriesOfOneGenerationIntoTheNext(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var summary = runner(generationalConfig(8), BY_LENGTH).run(corpus, 42, 1);

        assertEquals(WorkflowRunSummary.StopReason.COMPLETED, summary.stopReason());
        assertEquals(8, summary.corpus().totalEntries());
        assertEquals(1, summary.corpus().latestGeneration());
        assertEquals(4, summary.corpus().entries(0));
        assertEquals(4, summary.corpus().entries(1));
        var parents = new HashSet<String>();
        var mutants = new ArrayList<GenerationMetadata>();
        for (var verdict : CorpusStage.QUALITY.resultVerdicts()) {
            for (var stored : corpus.resultEntries(CorpusStage.QUALITY, verdict)) {
                var generation = CorpusEnvelopeCodec.decodeEnvelope(stored.read()).generation().orElseThrow();
                if (verdict == CorpusVerdict.PASS && generation.generation().getAsInt() == 0) {
                    parents.add(stored.digest());
                }
                generation.mutation().ifPresent(mutation -> mutants.add(generation));
            }
        }
        assertFalse(parents.isEmpty());
        assertEquals(2, mutants.size());
        for (var mutant : mutants) {
            assertEquals(1, mutant.generation().getAsInt());
            assertTrue(parents.contains(mutant.mutation().orElseThrow().parent()));
        }
        assertEquals(
                0, corpus.resultEntries(CorpusStage.AGGREGATOR, CorpusVerdict.PASS).size(),
                "every agreeing entry of both generations passed through the gate");
    }

    @Test
    void resumesTheLatestGenerationWhereTheCorpusLimitStoppedIt(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));

        var first = runner(generationalConfig(6), BY_LENGTH).run(corpus, 42, 1);
        var second = runner(generationalConfig(8), BY_LENGTH).run(corpus, 42, 1);

        assertEquals(2, first.corpus().entries(1));
        assertEquals(1, second.corpus().latestGeneration());
        assertEquals(4, second.corpus().entries(1));
        assertEquals(8, second.corpus().totalEntries());
        assertEquals(0, corpus.resultEntries(CorpusStage.AGGREGATOR, CorpusVerdict.PASS).size());
    }

    @Test
    void resumesAnUnfinishedGenerationWithPbtAlreadyAdmittedAhead(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        for (var index = 0; index < 4; index++) {
            corpus.store(InputKind.EXPRESSION, new byte[] {(byte) index},
                    GenerationMetadata.generated(0, 0, 0));
        }
        corpus.store(InputKind.EXPRESSION, new byte[] {10, 11},
                GenerationMetadata.generated(1, 0, 0));

        var summary = runner(generationalConfig(8), BY_LENGTH).run(corpus, 42, 1);

        assertEquals(WorkflowRunSummary.StopReason.COMPLETED, summary.stopReason());
        assertEquals(4, summary.corpus().entries(0));
        assertEquals(4, summary.corpus().entries(1));
        assertEquals(2, summary.corpus().mutants(1));
        assertEquals(0, summary.corpus().unsettled().size());
    }

    @Test
    void continuesAFinishedCorpusAfterGenerationSizeIsRaised(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var first = runner(generationalConfig(12), BY_LENGTH).run(corpus, 42, 1);
        assertEquals(2, first.corpus().latestGeneration());

        // Every generation is gated, but each now looks short of the new size. Being short is not
        // evidence of an unfinished generation, so the run continues instead of refusing.
        var second = runner(withGenerationSize(generationalConfig(24), 8), BY_LENGTH).run(corpus, 42, 1);

        assertEquals(WorkflowRunSummary.StopReason.COMPLETED, second.stopReason());
        assertEquals(0, second.corpus().unsettled().size());
        assertEquals(0, corpus.resultEntries(CorpusStage.AGGREGATOR, CorpusVerdict.PASS).size());
    }

    @Test
    void refusesACorpusWhoseUnfinishedGenerationIsMoreThanOneBehindItsLatest(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        // Generation 0 is two entries short while generation 2 has been admitted, which this
        // workflow never produces: it admits at most one generation ahead of the one it gates.
        corpus.store(InputKind.EXPRESSION, new byte[] {0}, GenerationMetadata.generated(0, 0, 0));
        corpus.store(InputKind.EXPRESSION, new byte[] {1, 1}, GenerationMetadata.generated(0, 0, 0));
        corpus.store(InputKind.EXPRESSION, new byte[] {2, 2, 2}, GenerationMetadata.generated(2, 0, 0));

        var runner = runner(generationalConfig(12), BY_LENGTH);
        var exception = assertThrows(CorpusException.class, () -> runner.run(corpus, 42, 1));

        assertTrue(exception.getMessage().contains("generation 0 has unfinished or ungated entries"),
                exception.getMessage());
    }

    @Test
    void startsNextGenerationPbtWhileAnOlderCheckerIsStillRunning(@TempDir Path directory)
            throws Exception {
        Assumptions.assumeTrue(Runtime.getRuntime().availableProcessors() >= 2);
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        corpus.store(InputKind.EXPRESSION, new byte[] {0}, GenerationMetadata.generated(0, 0, 0));
        corpus.store(InputKind.EXPRESSION, new byte[] {1, 2}, GenerationMetadata.generated(0, 0, 0));
        corpus.store(InputKind.EXPRESSION, new byte[] {2, 3, 4}, GenerationMetadata.generated(0, 0, 0));
        var base = generationalConfig(6);
        var config = new FuzzTlaConfig(
                base.generatedKind(), base.generator(), base.workflow(), base.pbt(),
                new MutatorConfig(3, 2.0 / 3.0, 1, Map.of(MutationOperator.INSERT, 1),
                        base.mutator().gate()),
                base.libraries());
        var checkerEntered = new CountDownLatch(1);
        var releaseChecker = new CountDownLatch(1);
        var lookaheadStarted = new CountDownLatch(1);
        var held = new AtomicBoolean();
        Generator<TlaEx> delayed = draw -> {
            var marker = draw.drawByte();
            var thread = Thread.currentThread().getName();
            try {
                if (thread.startsWith("fuzztla-tlc-") && marker == 0
                        && held.compareAndSet(false, true)) {
                    checkerEntered.countDown();
                    if (!releaseChecker.await(30, TimeUnit.SECONDS)) {
                        throw new AssertionError("checker was not released");
                    }
                }
                if (thread.startsWith("fuzztla-input-")) {
                    if (!checkerEntered.await(30, TimeUnit.SECONDS)) {
                        throw new AssertionError("older checker did not start");
                    }
                    lookaheadStarted.countDown();
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError(exception);
            }
            return BY_LENGTH.generate(draw);
        };

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var run = executor.submit(() -> runner(config, delayed).run(corpus, 42, 2));
            try {
                assertTrue(checkerEntered.await(30, TimeUnit.SECONDS));
                assertTrue(lookaheadStarted.await(30, TimeUnit.SECONDS));
                assertFalse(run.isDone(), "the older checker must still be running");
            } finally {
                releaseChecker.countDown();
            }
            assertEquals(WorkflowRunSummary.StopReason.COMPLETED,
                    run.get(60, TimeUnit.SECONDS).stopReason());
        }
    }

    @Test
    void parserCapacityStopsGracefullyAndLeavesUpstreamInputs(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = config(5, 5, 0, 16);
        var unbound = new TlaTypedScopeUncheckedBuilder()
                .name("missing", TlaTypes.BOOL);
        Generator<TlaEx> generator = _ -> unbound;
        var runner = runner(config, generator);
        for (var candidate = 0; candidate < 5; candidate++) {
            corpus.store(InputKind.EXPRESSION, new byte[] {(byte) candidate}, ADMITTED);
        }

        var first = runner.run(
                corpus, 9, Math.min(2, Runtime.getRuntime().availableProcessors()));
        var second = runner.run(corpus, 9, 1);

        assertEquals(WorkflowRunSummary.StopReason.CAPACITY_REACHED, first.stopReason());
        assertEquals(0, first.corpus().processedEntries(CorpusStage.PARSER));
        assertTrue(first.corpus().pendingEntries(CorpusStage.PARSER) > 0);
        assertEquals(WorkflowRunSummary.StopReason.CAPACITY_REACHED, second.stopReason());
        assertEquals(first.corpus().totalEntries(), second.corpus().totalEntries());
        assertEquals(first.corpus().pendingEntries(CorpusStage.PARSER), second.corpus().pendingEntries(CorpusStage.PARSER));
    }

    @Test
    void parserPassesDoNotConsumeRetainedResultCapacity(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = config(4, 4, 1, 16);
        var expression = IrGenerators.expressions(config.generator()).generate(new byte[0]);
        Generator<TlaEx> constant = _ -> expression;
        for (var value = 0; value < 4; value++) {
            corpus.store(InputKind.EXPRESSION, new byte[] {(byte) value}, ADMITTED);
        }

        var summary = runner(config, constant).run(
                corpus,
                42,
                Math.min(2, Runtime.getRuntime().availableProcessors()));

        assertEquals(WorkflowRunSummary.StopReason.COMPLETED, summary.stopReason());
        assertEquals(4, summary.stage(CorpusStage.PARSER).count(CorpusVerdict.PASS));
        assertEquals(0, summary.corpus().resultEntries(CorpusStage.PARSER));
        assertEquals(4, summary.corpus().counts(CorpusStage.TLC).count(CorpusVerdict.PASS));
        assertEquals(4, summary.corpus().counts(CorpusStage.APALACHE).count(CorpusVerdict.PASS));
        assertEquals(4, summary.corpus().counts(CorpusStage.AGGREGATOR).count(CorpusVerdict.PASS));
        assertEquals(0, summary.corpus().resultEntries(CorpusStage.TLC));
        assertEquals(0, summary.corpus().resultEntries(CorpusStage.APALACHE));
    }

    @Test
    void fullTlcResultCapacityDoesNotBlockParserFailures(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = new FuzzTlaConfig(
                InputKind.EXPRESSION,
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        2,
                        InputStageConfig.of(2),
                        new ParserStageConfig(2, 10),
                        Map.of(
                                CorpusStage.TLC,
                                new CheckerStageConfig(0, 10, 512, 1),
                                CorpusStage.APALACHE,
                                new CheckerStageConfig(2, 10, 512, 1))),
                new PbtConfig(16, 10, 2.0, 1.5), MutatorConfig.defaults(), OperatorLibraryConfig.empty());
        var unbound = new TlaTypedScopeUncheckedBuilder()
                .name("missing", TlaTypes.BOOL);
        Generator<TlaEx> generator = _ -> unbound;
        corpus.store(InputKind.EXPRESSION, new byte[] {0}, ADMITTED);
        corpus.store(InputKind.EXPRESSION, new byte[] {1}, ADMITTED);

        var summary = runner(config, generator).run(corpus, 42, 1);

        assertEquals(WorkflowRunSummary.StopReason.COMPLETED, summary.stopReason());
        assertEquals(2, summary.stage(CorpusStage.PARSER).count(CorpusVerdict.FAIL));
        assertEquals(0, summary.corpus().pendingEntries(CorpusStage.PARSER));
        assertEquals(0, summary.corpus().processedEntries(CorpusStage.TLC));
    }

    @Test
    void identifiesAndPreservesAnInputThatCrashesParserPreparation(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = config(1, 1, 1, 0);
        var input = new byte[0];
        corpus.store(InputKind.EXPRESSION, input, ADMITTED);
        var source = corpus.inputPath(input);
        var delegate = IrGenerators.expressions(config.generator());
        Generator<TlaEx> overflowInParser = payload -> {
            if (Thread.currentThread().getName().startsWith("fuzztla-parser-")) {
                throw new StackOverflowError("parser preparation overflow");
            }
            return delegate.generate(payload);
        };

        var failure = assertThrows(
                WorkflowException.class,
                () -> runner(config, overflowInParser).run(corpus, 42, 1));

        var candidate = corpus.resolve(CorpusPath.GENERATOR_CRASH).resolve(source.getFileName());
        var reportName = source.getFileName()
                .toString()
                .replace(".cbor", CorpusDirectory.CRASH_REPORT_EXTENSION);
        var report = corpus.resolve(CorpusPath.GENERATOR_CRASH).resolve(reportName);
        assertTrue(failure.getMessage().contains(source.toString()));
        assertTrue(failure.getMessage().contains(candidate.toString()));
        assertTrue(Files.readString(report)
                .contains("StackOverflowError: parser preparation overflow"));
        assertEquals(1, corpus.recoverAndValidate(CorpusEntryValidator.NONE).totalEntries());
        assertTrue(corpus.readRunStatistics().stageElapsedNanos(CorpusStage.PARSER) > 0);
        assertTrue(corpus.readRunStatistics().totalElapsedNanos() > 0);
    }

    @Test
    void runsEachStoredInputThroughTheDecoderItsKindNames(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(
                directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        corpus.store(InputKind.EXPRESSION, new byte[] {0}, ADMITTED);
        corpus.store(InputKind.MODULE, new byte[0], ADMITTED);
        var config = new FuzzTlaConfig(
                InputKind.EXPRESSION,
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        2,
                        InputStageConfig.of(2),
                        new ParserStageConfig(2, 10),
                        Map.of(
                                CorpusStage.TLC,
                                new CheckerStageConfig(2, 10, 512, 1),
                                CorpusStage.APALACHE,
                                new CheckerStageConfig(2, 10, 512, 1))),
                new PbtConfig(4, 10, 2.0, 1.5), MutatorConfig.defaults(), OperatorLibraryConfig.empty());

        var summary = new WorkflowRunner(config, Technique.PBT).run(corpus, 42, 1);

        assertEquals(WorkflowRunSummary.StopReason.COMPLETED, summary.stopReason());
        assertEquals(2, summary.stage(CorpusStage.PARSER).count(CorpusVerdict.PASS));
        assertEquals(2, summary.corpus().totalEntries());
    }

    @Test
    void rejectsMoreTlcWorkersThanTheSharedCpuBudget(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = new FuzzTlaConfig(
                InputKind.EXPRESSION,
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        0,
                        InputStageConfig.of(0),
                        new ParserStageConfig(0, 10),
                        Map.of(
                                CorpusStage.TLC,
                                new CheckerStageConfig(0, 10, 512, 2),
                                CorpusStage.APALACHE,
                                new CheckerStageConfig(0, 10, 512, 1))),
                new PbtConfig(0, 10, 2.0, 1.5), MutatorConfig.defaults(), OperatorLibraryConfig.empty());

        var failure = assertThrows(
                WorkflowException.class,
                () -> new WorkflowRunner(config, Technique.PBT).run(corpus, 42, 1));

        assertTrue(failure.getMessage().contains("workflow.tlc.workers"));
        assertTrue(failure.getMessage().contains("--max-cpus"));
    }

    @Test
    void rejectsMoreApalacheWorkersThanTheSharedCpuBudget(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = new FuzzTlaConfig(
                InputKind.EXPRESSION,
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        0,
                        InputStageConfig.of(0),
                        new ParserStageConfig(0, 10),
                        Map.of(
                                CorpusStage.TLC,
                                new CheckerStageConfig(0, 10, 512, 1),
                                CorpusStage.APALACHE,
                                new CheckerStageConfig(0, 10, 512, 2))),
                new PbtConfig(0, 10, 2.0, 1.5), MutatorConfig.defaults(), OperatorLibraryConfig.empty());

        var failure = assertThrows(
                WorkflowException.class,
                () -> new WorkflowRunner(config, Technique.PBT).run(corpus, 42, 1));

        assertTrue(failure.getMessage().contains("workflow.apalache.workers"));
        assertTrue(failure.getMessage().contains("--max-cpus"));
    }

    @Test
    void tlcCapacityStopsGracefullyAndLeavesItsInput(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = new FuzzTlaConfig(
                InputKind.EXPRESSION,
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        1,
                        InputStageConfig.of(1),
                        new ParserStageConfig(1, 10),
                        Map.of(
                                CorpusStage.TLC,
                                new CheckerStageConfig(0, 10, 512, 1),
                                CorpusStage.APALACHE,
                                new CheckerStageConfig(1, 10, 512, 1))),
                new PbtConfig(16, 10, 2.0, 1.5), MutatorConfig.defaults(), OperatorLibraryConfig.empty());
        var generator = IrGenerators.expressions(config.generator());
        Path source = null;
        for (var candidate = 0; source == null; candidate++) {
            var input = new byte[] {(byte) candidate};
            try {
                generator.generate(input);
                corpus.store(InputKind.EXPRESSION, input, ADMITTED);
                source = corpus.inputPath(input);
            } catch (InputRejectedException ignored) {
                // Find one accepted deterministic input.
            }
        }
        var parserPass = corpus.completeParser(
                source,
                new StageResult(CorpusVerdict.PASS, Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)));
        var tlcInput = corpus.resolve(CorpusPath.TLC_INPUT).resolve(parserPass.getFileName());
        corpus.fanOutParserPass(parserPass);

        var summary = new WorkflowRunner(config, Technique.PBT).run(corpus, 42, 1);

        assertEquals(WorkflowRunSummary.StopReason.CAPACITY_REACHED, summary.stopReason());
        assertEquals(1, summary.corpus().pendingEntries(CorpusStage.TLC));
        assertTrue(Files.exists(tlcInput));
    }

    @Test
    void apalacheCapacityStopsGracefullyAndLeavesItsInput(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = new FuzzTlaConfig(
                InputKind.EXPRESSION,
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        1,
                        InputStageConfig.of(1),
                        new ParserStageConfig(1, 10),
                        Map.of(
                                CorpusStage.TLC,
                                new CheckerStageConfig(1, 10, 512, 1),
                                CorpusStage.APALACHE,
                                new CheckerStageConfig(0, 10, 512, 1))),
                new PbtConfig(16, 10, 2.0, 1.5), MutatorConfig.defaults(), OperatorLibraryConfig.empty());
        var generator = IrGenerators.expressions(config.generator());
        Path source = null;
        for (var candidate = 0; source == null; candidate++) {
            var input = new byte[] {(byte) candidate};
            try {
                generator.generate(input);
                corpus.store(InputKind.EXPRESSION, input, ADMITTED);
                source = corpus.inputPath(input);
            } catch (InputRejectedException ignored) {
                // Find one accepted deterministic input.
            }
        }
        var parserPass = corpus.completeParser(
                source,
                new StageResult(CorpusVerdict.PASS, Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)));
        var apalacheInput =
                corpus.resolve(CorpusPath.APALACHE_INPUT).resolve(parserPass.getFileName());
        corpus.fanOutParserPass(parserPass);

        var summary = new WorkflowRunner(config, Technique.PBT).run(corpus, 42, 1);

        assertEquals(WorkflowRunSummary.StopReason.CAPACITY_REACHED, summary.stopReason());
        assertEquals(1, summary.corpus().pendingEntries(CorpusStage.APALACHE));
        assertTrue(Files.exists(apalacheInput));
    }

    @Test
    void concurrentTlcWorkersFillTheLastAvailableResultSlot(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = new FuzzTlaConfig(
                InputKind.EXPRESSION,
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        2,
                        InputStageConfig.of(2),
                        new ParserStageConfig(2, 10),
                        Map.of(
                                CorpusStage.TLC,
                                new CheckerStageConfig(1, 10, 512, 1),
                                CorpusStage.APALACHE,
                                new CheckerStageConfig(2, 10, 512, 1))),
                new PbtConfig(16, 10, 2.0, 1.5), MutatorConfig.defaults(), OperatorLibraryConfig.empty());
        var expression = IrGenerators.expressions(config.generator()).generate(new byte[0]);
        Generator<TlaEx> generator = _ -> expression;
        for (var value = 0; value < 2; value++) {
            var payload = new byte[] {(byte) value};
            corpus.store(InputKind.EXPRESSION, payload, ADMITTED);
            var parserPass = corpus.completeParser(
                corpus.inputPath(payload),
                new StageResult(CorpusVerdict.PASS, Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)));
            corpus.fanOutParserPass(parserPass);
        }

        var summary = runner(config, generator).run(
                corpus,
                42,
                Math.min(2, Runtime.getRuntime().availableProcessors()));

        assertEquals(WorkflowRunSummary.StopReason.CAPACITY_REACHED, summary.stopReason());
        assertEquals(1, summary.corpus().processedEntries(CorpusStage.TLC));
        assertEquals(1, summary.corpus().pendingEntries(CorpusStage.TLC));
    }

    private WorkflowRunner runner(FuzzTlaConfig config, Generator<TlaEx> expressions)
            throws WorkflowException {
        return new WorkflowRunner(
                config,
                Technique.PBT,
                SpecDecoders.of(config.generator()).replacingExpressions(expressions));
    }

    /** Decodes every input to {@code n = n} for its length n, so only length changes change the module. */
    private static final Generator<TlaEx> BY_LENGTH = draw -> {
        var builder = new TlaTypedScopeUncheckedBuilder();
        return builder.eql(builder.integer(draw.remaining()), builder.integer(draw.remaining()));
    };

    /**
     * Generations of four entries, all agreeing entries kept, half of each generation mutated by
     * insertion. Expression inputs are always shallow, so no shallow pattern is enabled.
     */
    private FuzzTlaConfig generationalConfig(int total) {
        var base = config(total, total, total, 8);
        return new FuzzTlaConfig(
                base.generatedKind(),
                base.generator(),
                base.workflow(),
                new PbtConfig(8, 1, 2.0, 1.5),
                new MutatorConfig(
                        4, 0.5, 1, Map.of(MutationOperator.INSERT, 1), new QualityGateConfig(1.0, Set.of(), 0, false)),
                base.libraries());
    }

    private static FuzzTlaConfig withGenerationSize(FuzzTlaConfig base, int generationSize) {
        var mutator = base.mutator();
        return new FuzzTlaConfig(
                base.generatedKind(),
                base.generator(),
                base.workflow(),
                base.pbt(),
                new MutatorConfig(
                        generationSize,
                        mutator.feedbackRatio(),
                        mutator.maximumEdits(),
                        mutator.weights(),
                        mutator.gate()),
                base.libraries());
    }

    private FuzzTlaConfig config(
            int total, int inputs, int parser, int maximumInputBytes) {
        return new FuzzTlaConfig(
                InputKind.EXPRESSION,
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        total,
                        InputStageConfig.of(inputs),
                        new ParserStageConfig(parser, 10),
                        Map.of(
                                CorpusStage.TLC,
                                new CheckerStageConfig(total, 10, 512, 1),
                                CorpusStage.APALACHE,
                                new CheckerStageConfig(total, 10, 512, 1))),
                new PbtConfig(maximumInputBytes, 10, 2.0, 1.5), MutatorConfig.defaults(), OperatorLibraryConfig.empty());
    }

}
