package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.BoolT1$;
import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.config.ApalacheStageConfig;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.ParserStageConfig;
import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.config.StageConfig;
import io.github.tlaplus.hardening.config.TlcStageConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.config.WorkflowConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEntryValidator;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.StageResult;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowRunnerTest {
    @Test
    void reportsInitialAndFinalProgressSnapshots(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = config(8, 3, 8, 16);
        var observed = new CopyOnWriteArrayList<WorkflowProgress>();

        var summary = new WorkflowRunner(config).run(corpus, 42, 1, observed::add);

        assertTrue(observed.size() >= 2);
        var initial = observed.getFirst();
        assertEquals(WorkflowProgress.Phase.RUNNING, initial.phase());
        assertEquals(42, initial.generator().seed());
        assertEquals(0, initial.corpusEntries());
        assertEquals(0, initial.awaitingParser());
        assertEquals(0, initial.awaitingTlc());
        assertEquals(0, initial.awaitingApalache());

        var last = observed.getLast();
        assertEquals(WorkflowProgress.Phase.FINALIZING, last.phase());
        assertEquals(summary.generator(), last.generator());
        assertEquals(summary.parser(), last.parser());
        assertEquals(summary.corpus().totalEntries(), last.corpusEntries());
        assertEquals(summary.corpus().inputEntries(), last.awaitingParser());
        assertEquals(summary.corpus().tlcInputEntries(), last.awaitingTlc());
        assertEquals(summary.corpus().apalacheInputEntries(), last.awaitingApalache());

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
            assertTrue(progress.parser().processed() >= parsed);
            assertTrue(progress.awaitingParser() >= 0);
            assertTrue(progress.awaitingTlc() >= 0);
            assertTrue(progress.awaitingApalache() >= 0);
            assertTrue(progress.totalElapsed().compareTo(totalElapsed) >= 0);
            generated = progress.generator().generated();
            corpusEntries = progress.corpusEntries();
            parsed = progress.parser().processed();
            totalElapsed = progress.totalElapsed();
        }
    }

    @Test
    void savesAndRecoversCumulativeStatisticsAcrossRuns(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = config(1, 1, 1, 0);
        var expression = new TlaTypedScopeUncheckedBuilder()
                .name("missing", BoolT1$.MODULE$);
        Generator<TlaEx> generator = _ -> expression;
        var input = new byte[] {1};
        corpus.store(input);
        corpus.completeParser(
                corpus.inputPath(input),
                new StageResult(CorpusVerdict.FAIL, Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)));
        var runner = new WorkflowRunner(config, generator);

        var first = runner.run(corpus, 42, 1);
        var second = runner.run(corpus, 43, 1);
        var saved = corpus.readRunStatistics();

        assertEquals(1, first.generator().generated());
        assertEquals(1, second.generator().generated());
        assertEquals(1, first.parser().failed());
        assertEquals(1, second.parser().failed());
        assertEquals(Duration.ZERO, first.parser().elapsed());
        assertEquals(Duration.ZERO, second.parser().elapsed());
        assertTrue(second.totalElapsed().compareTo(first.totalElapsed()) >= 0);
        assertEquals(second.totalElapsed().toNanos(), saved.totalElapsedNanos());
        assertEquals(second.parser().elapsed().toNanos(), saved.parserElapsedNanos());
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
                new WorkflowRunner(config, blocking).run(corpus, 42, 1);
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
        assertEquals(1, saved.generatorAttempts());
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
                () -> new WorkflowRunner(config).run(corpus, 42, 1, _ -> {
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

        var summary = new WorkflowRunner(config)
                .run(corpus, 42, Math.min(2, Runtime.getRuntime().availableProcessors()));

        assertEquals(WorkflowRunSummary.StopReason.COMPLETED, summary.stopReason());
        assertEquals(12, summary.generator().generated());
        assertEquals(12, summary.parser().processed());
        assertEquals(12, summary.corpus().totalEntries());
        assertEquals(0, summary.corpus().inputEntries());
        assertFalse(Files.exists(corpus.resolve(CorpusPath.PARSER_SCRATCH)));
        assertFalse(Files.exists(corpus.resolve(CorpusPath.TLC_SCRATCH)));
        assertFalse(Files.exists(corpus.resolve(CorpusPath.APALACHE_SCRATCH)));
    }

    @Test
    void parserCapacityStopsGracefullyAndLeavesUpstreamInputs(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = config(5, 5, 0, 16);
        var unbound = new TlaTypedScopeUncheckedBuilder()
                .name("missing", BoolT1$.MODULE$);
        Generator<TlaEx> generator = _ -> unbound;
        var runner = new WorkflowRunner(config, generator);
        for (var candidate = 0; candidate < 5; candidate++) {
            corpus.store(new byte[] {(byte) candidate});
        }

        var first = runner.run(
                corpus, 9, Math.min(2, Runtime.getRuntime().availableProcessors()));
        var second = runner.run(corpus, 9, 1);

        assertEquals(WorkflowRunSummary.StopReason.CAPACITY_REACHED, first.stopReason());
        assertEquals(0, first.corpus().parserEntries());
        assertTrue(first.corpus().inputEntries() > 0);
        assertEquals(WorkflowRunSummary.StopReason.CAPACITY_REACHED, second.stopReason());
        assertEquals(first.corpus().totalEntries(), second.corpus().totalEntries());
        assertEquals(first.corpus().inputEntries(), second.corpus().inputEntries());
    }

    @Test
    void parserPassesDoNotConsumeRetainedResultCapacity(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = config(4, 4, 1, 16);
        var expression = IrGenerators.expressions(config.generator()).generate(new byte[0]);
        Generator<TlaEx> constant = _ -> expression;
        for (var value = 0; value < 4; value++) {
            corpus.store(new byte[] {(byte) value});
        }

        var summary = new WorkflowRunner(config, constant).run(
                corpus,
                42,
                Math.min(2, Runtime.getRuntime().availableProcessors()));

        assertEquals(WorkflowRunSummary.StopReason.COMPLETED, summary.stopReason());
        assertEquals(4, summary.parser().passed());
        assertEquals(0, summary.corpus().parserResultEntries());
        assertEquals(4, summary.corpus().tlcPassEntries());
        assertEquals(4, summary.corpus().apalachePassEntries());
    }

    @Test
    void fullTlcResultCapacityDoesNotBlockParserFailures(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = new FuzzTlaConfig(
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        2,
                        new StageConfig(2),
                        new ParserStageConfig(2, 10),
                        new TlcStageConfig(0, 10, 512, 1),
                        new ApalacheStageConfig(2, 10, 512, 1)),
                new PbtConfig(16, 10, 2.0, 1.5));
        var unbound = new TlaTypedScopeUncheckedBuilder()
                .name("missing", BoolT1$.MODULE$);
        Generator<TlaEx> generator = _ -> unbound;
        corpus.store(new byte[] {0});
        corpus.store(new byte[] {1});

        var summary = new WorkflowRunner(config, generator).run(corpus, 42, 1);

        assertEquals(WorkflowRunSummary.StopReason.COMPLETED, summary.stopReason());
        assertEquals(2, summary.parser().failed());
        assertEquals(0, summary.corpus().inputEntries());
        assertEquals(0, summary.corpus().tlcEntries());
    }

    @Test
    void identifiesAndPreservesAnInputThatCrashesParserPreparation(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = config(1, 1, 1, 0);
        var input = new byte[0];
        corpus.store(input);
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
                () -> new WorkflowRunner(config, overflowInParser).run(corpus, 42, 1));

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
        assertTrue(corpus.readRunStatistics().parserElapsedNanos() > 0);
        assertTrue(corpus.readRunStatistics().totalElapsedNanos() > 0);
    }

    @Test
    void rejectsMoreTlcWorkersThanTheSharedCpuBudget(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = new FuzzTlaConfig(
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        0,
                        new StageConfig(0),
                        new ParserStageConfig(0, 10),
                        new TlcStageConfig(0, 10, 512, 2),
                        new ApalacheStageConfig(0, 10, 512, 1)),
                new PbtConfig(0, 10, 2.0, 1.5));

        var failure = assertThrows(
                WorkflowException.class,
                () -> new WorkflowRunner(config).run(corpus, 42, 1));

        assertTrue(failure.getMessage().contains("workflow.tlc.workers"));
        assertTrue(failure.getMessage().contains("--max-cpus"));
    }

    @Test
    void rejectsMoreApalacheWorkersThanTheSharedCpuBudget(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = new FuzzTlaConfig(
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        0,
                        new StageConfig(0),
                        new ParserStageConfig(0, 10),
                        new TlcStageConfig(0, 10, 512, 1),
                        new ApalacheStageConfig(0, 10, 512, 2)),
                new PbtConfig(0, 10, 2.0, 1.5));

        var failure = assertThrows(
                WorkflowException.class,
                () -> new WorkflowRunner(config).run(corpus, 42, 1));

        assertTrue(failure.getMessage().contains("workflow.apalache.workers"));
        assertTrue(failure.getMessage().contains("--max-cpus"));
    }

    @Test
    void tlcCapacityStopsGracefullyAndLeavesItsInput(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = new FuzzTlaConfig(
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        1,
                        new StageConfig(1),
                        new ParserStageConfig(1, 10),
                        new TlcStageConfig(0, 10, 512, 1),
                        new ApalacheStageConfig(1, 10, 512, 1)),
                new PbtConfig(16, 10, 2.0, 1.5));
        var generator = IrGenerators.expressions(config.generator());
        Path source = null;
        for (var candidate = 0; source == null; candidate++) {
            var input = new byte[] {(byte) candidate};
            try {
                generator.generate(input);
                corpus.store(input);
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

        var summary = new WorkflowRunner(config).run(corpus, 42, 1);

        assertEquals(WorkflowRunSummary.StopReason.CAPACITY_REACHED, summary.stopReason());
        assertEquals(1, summary.corpus().tlcInputEntries());
        assertTrue(Files.exists(tlcInput));
    }

    @Test
    void apalacheCapacityStopsGracefullyAndLeavesItsInput(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = new FuzzTlaConfig(
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        1,
                        new StageConfig(1),
                        new ParserStageConfig(1, 10),
                        new TlcStageConfig(1, 10, 512, 1),
                        new ApalacheStageConfig(0, 10, 512, 1)),
                new PbtConfig(16, 10, 2.0, 1.5));
        var generator = IrGenerators.expressions(config.generator());
        Path source = null;
        for (var candidate = 0; source == null; candidate++) {
            var input = new byte[] {(byte) candidate};
            try {
                generator.generate(input);
                corpus.store(input);
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

        var summary = new WorkflowRunner(config).run(corpus, 42, 1);

        assertEquals(WorkflowRunSummary.StopReason.CAPACITY_REACHED, summary.stopReason());
        assertEquals(1, summary.corpus().apalacheInputEntries());
        assertTrue(Files.exists(apalacheInput));
    }

    @Test
    void concurrentTlcWorkersFillTheLastAvailableResultSlot(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var config = new FuzzTlaConfig(
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        2,
                        new StageConfig(2),
                        new ParserStageConfig(2, 10),
                        new TlcStageConfig(1, 10, 512, 1),
                        new ApalacheStageConfig(2, 10, 512, 1)),
                new PbtConfig(16, 10, 2.0, 1.5));
        var expression = IrGenerators.expressions(config.generator()).generate(new byte[0]);
        Generator<TlaEx> generator = _ -> expression;
        for (var value = 0; value < 2; value++) {
            var payload = new byte[] {(byte) value};
            corpus.store(payload);
            var parserPass = corpus.completeParser(
                corpus.inputPath(payload),
                new StageResult(CorpusVerdict.PASS, Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)));
            corpus.fanOutParserPass(parserPass);
        }

        var summary = new WorkflowRunner(config, generator).run(
                corpus,
                42,
                Math.min(2, Runtime.getRuntime().availableProcessors()));

        assertEquals(WorkflowRunSummary.StopReason.CAPACITY_REACHED, summary.stopReason());
        assertEquals(1, summary.corpus().tlcEntries());
        assertEquals(1, summary.corpus().tlcInputEntries());
    }

    private FuzzTlaConfig config(
            int total, int inputs, int parser, int maximumInputBytes) {
        return new FuzzTlaConfig(
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        total,
                        new StageConfig(inputs),
                        new ParserStageConfig(parser, 10),
                        new TlcStageConfig(total, 10, 512, 1),
                        new ApalacheStageConfig(total, 10, 512, 1)),
                new PbtConfig(maximumInputBytes, 10, 2.0, 1.5));
    }

}
