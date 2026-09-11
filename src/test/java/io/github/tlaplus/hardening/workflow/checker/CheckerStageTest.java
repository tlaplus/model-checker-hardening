package io.github.tlaplus.hardening.workflow.checker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusEntryValidator;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.StageResult;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.workflow.execution.CpuBudget;
import io.github.tlaplus.hardening.workflow.execution.ElapsedTimeAccumulator;
import io.github.tlaplus.hardening.workflow.execution.OccupancyGate;
import io.github.tlaplus.hardening.workflow.execution.StageCounters;
import io.github.tlaplus.hardening.workflow.execution.StageEnvironment;
import io.github.tlaplus.hardening.workflow.execution.StageVerdictSummary;
import io.github.tlaplus.hardening.workflow.execution.WorkQueue;
import io.github.tlaplus.hardening.workflow.execution.WorkflowControl;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CheckerStageTest {
    @Test
    void replacesAWorkerAfterACrashAndClosesTheReplacement(@TempDir Path directory)
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
        var stage = new CheckerStage(
                backend,
                new OccupancyGate(0, 2),
                new StageCounters(initial, new ElapsedTimeAccumulator()),
                new StageEnvironment(corpus, decoders, new CpuBudget(1), control),
                input,
                output);
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
        var inventory = corpus.recoverAndValidate(CorpusEntryValidator.NONE);
        assertEquals(1, inventory.counts(CorpusStage.TLC).count(CorpusVerdict.PASS));
        assertEquals(1, inventory.counts(CorpusStage.TLC).count(CorpusVerdict.CRASH));
    }

    private static final class RestartingBackend implements CheckerBackend {
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
        public Function<TlaModule, String> renderer() {
            return module -> {
                renders.incrementAndGet();
                return "backend-specific input";
            };
        }

        @Override
        public CheckerWorker startWorker() {
            var ordinal = starts.getAndIncrement();
            return new CheckerWorker() {
                @Override
                public ToolResult check(ToolInput input) {
                    assertEquals("backend-specific input", input.text());
                    return ordinal == 0
                            ? new ToolResult(StageOutcome.CRASH, "deliberate crash")
                            : new ToolResult(StageOutcome.PASS, "pass");
                }

                @Override
                public void close() {
                    closes.incrementAndGet();
                }
            };
        }

        @Override
        public Optional<String> failureDetail(String diagnostic) {
            return Optional.empty();
        }
    }
}
