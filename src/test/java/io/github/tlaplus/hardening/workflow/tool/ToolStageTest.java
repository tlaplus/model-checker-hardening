package io.github.tlaplus.hardening.workflow.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.checker.ExplorationMetrics;
import io.github.tlaplus.hardening.checker.ExplorationPhase;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEntryValidator;
import io.github.tlaplus.hardening.corpus.CorpusEnvelopeCodec;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.StageResult;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.gen.trace.Trace;
import io.github.tlaplus.hardening.gen.trace.TraceEnding;
import io.github.tlaplus.hardening.gen.trace.TracePayload;
import io.github.tlaplus.hardening.workflow.checker.CheckerRouting;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.IgnoredEvents;
import io.github.tlaplus.hardening.workflow.execution.ElapsedTimeAccumulator;
import io.github.tlaplus.hardening.workflow.execution.OccupancyGate;
import io.github.tlaplus.hardening.workflow.execution.StageCounters;
import io.github.tlaplus.hardening.workflow.execution.StageEnvironment;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import io.github.tlaplus.hardening.workflow.spec.SpecText;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolStageTest {
    @Test
    void replacesAWorkerAfterACrashAndStoresTheReplacementsMetrics(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var expression = IrGenerators.expressions().generate(new byte[0]);
        var decoders = SpecDecoders.of(FuzzTlaConfig.defaults().generator())
                .replacingExpressions(_ -> expression);
        var input = new WorkQueue<Path>();
        for (var value = 0; value < 2; value++) {
            var payload = new byte[] {(byte) value};
            corpus.store(InputKind.EXPRESSION, payload);
            var parserPass = corpus.completeParser(
                corpus.inputPath(payload),
                new StageResult(CorpusVerdict.PASS, Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)));
            corpus.fanOutParserPass(parserPass);
            input.submit(corpus.resolve(CorpusPath.TLC_INPUT)
                    .resolve(parserPass.getFileName()));
        }
        input.close();

        var backend = new RestartingBackend();
        var output = new WorkQueue<Path>();
        var control = new WorkflowControl(input, output);
        var initial = StageVerdictSummary.empty();
        var stage = new ToolStage(
                backend,
                new CheckerRouting(new OccupancyGate(0, 2), output),
                new StageCounters(initial, new ElapsedTimeAccumulator()),
                new StageEnvironment(corpus, decoders, new CpuBudget(1), control, IgnoredEvents.INSTANCE),
                input);
        try {
            stage.start();
            stage.await();
        } finally {
            stage.close();
        }

        assertEquals(2, backend.starts.get());
        assertEquals(2, backend.closes.get());
        assertEquals(2, backend.renders.get());
        assertEquals(1, stage.summary().count(CorpusVerdict.PASS));
        assertEquals(1, stage.summary().count(CorpusVerdict.CRASH));
        assertEquals(2, output.size());
        var inventory = corpus.recoverAndValidate(CorpusEntryValidator.NONE);
        assertEquals(1, inventory.counts(CorpusStage.TLC).count(CorpusVerdict.PASS));
        assertEquals(1, inventory.counts(CorpusStage.TLC).count(CorpusVerdict.CRASH));
        try (var passes = Files.list(corpus.resolve(CorpusPath.TLC_PASS))) {
            var stored = CorpusEnvelopeCodec.decodeEnvelope(Files.readAllBytes(passes.findFirst().orElseThrow()));
            assertEquals(
                    Optional.of(RestartingBackend.METRICS),
                    stored.stages().getLast().metrics());
        }
    }

    /**
     * With replay on, the stage asks for the counterexample's trace and stores it at the types of
     * the module's variables (ADR 0034 §5); a trace that does not fit them is not stored.
     */
    @Test
    void storesTheTraceOfACounterexampleWhenAskedTo(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var decoders = SpecDecoders.of(FuzzTlaConfig.defaults().generator())
                .replacingExpressions(_ -> new TlaTypedScopeUncheckedBuilder().bool(false));
        var input = new WorkQueue<Path>();
        for (var value = 0; value < 2; value++) {
            var payload = new byte[] {(byte) value};
            corpus.store(InputKind.EXPRESSION, payload);
            var parserPass = corpus.completeParser(corpus.inputPath(payload),
                    new StageResult(CorpusVerdict.PASS, Instant.ofEpochSecond(1), Instant.ofEpochSecond(2)));
            corpus.fanOutParserPass(parserPass);
            input.submit(corpus.resolve(CorpusPath.TLC_INPUT).resolve(parserPass.getFileName()));
        }
        input.close();
        var output = new WorkQueue<Path>();
        var control = new WorkflowControl(input, output);
        var stage = new ToolStage(new TracingBackend(),
                new CheckerRouting(new OccupancyGate(0, 2), output),
                new StageCounters(StageVerdictSummary.empty(), new ElapsedTimeAccumulator()),
                new StageEnvironment(corpus, decoders, new CpuBudget(1), control, IgnoredEvents.INSTANCE, true),
                input);
        try {
            stage.start();
            stage.await();
        } finally {
            stage.close();
        }

        var traces = new ArrayList<Optional<Trace>>();
        try (var counterexamples = Files.list(corpus.resolve(CorpusPath.TLC_COUNTEREXAMPLE))) {
            for (var path : counterexamples.sorted().toList()) {
                var stored = CorpusEnvelopeCodec.decodeEnvelope(Files.readAllBytes(path)).stages().getLast();
                traces.add(stored.trace().map(trace -> TracePayload.decodeTrace(trace.bytes())));
            }
        }
        var expected = new Trace(List.of(List.of(JsonNodeFactory.instance.booleanNode(false))),
                new TraceEnding.Invariant());
        assertEquals(2, traces.size());
        assertTrue(traces.contains(Optional.of(expected)), traces.toString());
        assertTrue(traces.contains(Optional.empty()), traces.toString());
    }

    /** Reports a counterexample whose trace fits the module of the first input only. */
    private static final class TracingBackend implements ToolBackend {
        private final AtomicInteger checks = new AtomicInteger();

        @Override
        public CorpusStage stage() {
            return CorpusStage.TLC;
        }

        @Override
        public int workerCount() {
            return 1;
        }

        @Override
        public int cpuPermits() {
            return 1;
        }

        @Override
        public Function<SpecArtifact, String> renderer() {
            return SpecText::render;
        }

        @Override
        public ToolWorker startWorker() {
            return new ToolWorker() {
                @Override
                public ToolResult check(ToolInput input) {
                    assertTrue(input.request().recordsTrace());
                    var value = checks.getAndIncrement() == 0 ? "false" : "3";
                    return ToolResult.counterexample("violated")
                            .withTrace("{\"ending\": \"invariant\", \"states\": [{\"exprValue\": " + value + "}]}");
                }

                @Override
                public void close() {}
            };
        }
    }

    private static final class RestartingBackend implements ToolBackend {
        private static final ExplorationMetrics METRICS = ExplorationMetrics.builder()
                .phase(ExplorationPhase.COMPLETE)
                .count(ExplorationCount.DISTINCT_STATES, 6)
                .build();

        private final AtomicInteger starts = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private final AtomicInteger renders = new AtomicInteger();

        @Override
        public CorpusStage stage() {
            return CorpusStage.TLC;
        }

        @Override
        public int workerCount() {
            return 1;
        }

        @Override
        public int cpuPermits() {
            return 1;
        }

        @Override
        public Function<SpecArtifact, String> renderer() {
            return artifact -> {
                renders.incrementAndGet();
                return "backend-specific input";
            };
        }

        @Override
        public ToolWorker startWorker() {
            var ordinal = starts.getAndIncrement();
            return new ToolWorker() {
                @Override
                public ToolResult check(ToolInput input) {
                    assertEquals("backend-specific input", input.text());
                    return ordinal == 0
                            ? new ToolResult(StageOutcome.CRASH, "deliberate crash")
                            : new ToolResult(StageOutcome.PASS, "pass").withMetrics(METRICS);
                }

                @Override
                public void close() {
                    closes.incrementAndGet();
                }
            };
        }
    }
}
