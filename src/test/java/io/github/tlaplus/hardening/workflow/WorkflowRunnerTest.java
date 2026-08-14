package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.ParserConfig;
import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.config.StageConfig;
import io.github.tlaplus.hardening.config.WorkflowConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerators;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowRunnerTest {
    @Test
    void runsGenerationAndParsingToQuiescenceWithInputBackpressure(
            @TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var config = config(12, 3, 12, 16);

        var summary = new WorkflowRunner(config)
                .run(corpus, 42, Math.min(2, Runtime.getRuntime().availableProcessors()));

        assertEquals(WorkflowRunSummary.StopReason.COMPLETED, summary.stopReason());
        assertEquals(12, summary.generator().added());
        assertEquals(12, summary.parser().processed());
        assertEquals(12, summary.corpus().totalEntries());
        assertEquals(0, summary.corpus().inputEntries());
        assertFalse(Files.exists(corpus.root().resolve(".work").resolve("parser-tmp")));
    }

    @Test
    void oneGeneratorStreamIsReproducibleAcrossCpuBudgets(@TempDir Path directory)
            throws Exception {
        var first = CorpusDirectory.initialize(directory.resolve("first"));
        var second = CorpusDirectory.initialize(directory.resolve("second"));
        var config = config(10, 10, 10, 24);

        new WorkflowRunner(config).run(first, 1234, 1);
        new WorkflowRunner(config)
                .run(second, 1234, Math.min(2, Runtime.getRuntime().availableProcessors()));

        assertEquals(entryLocations(first), entryLocations(second));
    }

    @Test
    void parserCapacityStopsGracefullyAndLeavesUpstreamInputs(@TempDir Path directory)
            throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"));
        var config = config(10, 10, 3, 16);
        var runner = new WorkflowRunner(config);
        var generator = IrGenerators.expressions(config.generator());
        for (var candidate = 0; corpus.inventory(generator).inputEntries() < 5; candidate++) {
            var input = new byte[] {(byte) candidate};
            try {
                generator.generate(input);
                corpus.store(input);
            } catch (InputRejectedException ignored) {
                // Search the finite one-byte space for accepted deterministic inputs.
            }
        }

        var first = runner.run(
                corpus, 9, Math.min(2, Runtime.getRuntime().availableProcessors()));
        var second = runner.run(corpus, 9, 1);

        assertEquals(WorkflowRunSummary.StopReason.CAPACITY_REACHED, first.stopReason());
        assertEquals(3, first.corpus().parserEntries());
        assertTrue(first.corpus().inputEntries() > 0);
        assertEquals(WorkflowRunSummary.StopReason.CAPACITY_REACHED, second.stopReason());
        assertEquals(first.corpus().totalEntries(), second.corpus().totalEntries());
        assertEquals(first.corpus().inputEntries(), second.corpus().inputEntries());
    }

    private FuzzTlaConfig config(
            int total, int inputs, int parser, int maximumInputBytes) {
        return new FuzzTlaConfig(
                IrGenerationConfig.defaults(),
                new WorkflowConfig(
                        total, new StageConfig(inputs), new ParserConfig(parser, 10)),
                new PbtConfig(maximumInputBytes));
    }

    private Map<String, String> entryLocations(CorpusDirectory corpus) throws Exception {
        var result = new HashMap<String, String>();
        for (var directory : java.util.List.of(
                corpus.inputDirectory(),
                corpus.parserPassDirectory(),
                corpus.parserFailDirectory(),
                corpus.parserCrashDirectory())) {
            try (var paths = Files.list(directory)) {
                for (var path : paths.toList()) {
                    result.put(
                            path.getFileName().toString(),
                            directory.getFileName().toString());
                }
            }
        }
        return result;
    }
}
