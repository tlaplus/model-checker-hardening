package io.github.tlaplus.hardening.workflow.apalache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.GeneratedSpecSamples;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs generated modules with higher-order definitions and variant reads of names through the real
 * Apalache worker. Both shapes type-check only if the generator assigns the operator and variant
 * types Apalache infers, so a type-checking failure is a generator defect, and a crash is a
 * checker defect; any other verdict is acceptable.
 */
class GeneratedDefinitionShapesTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final CheckerStageConfig CONFIG = new CheckerStageConfig(10, 30, 1024, 1);

    @Test
    void apalacheTypeChecksOperatorArguments(@TempDir Path directory) throws Exception {
        assertTypeChecks(directory, GeneratedSpecSamples::passesOperatorArgument, 0x40E7L);
    }

    @Test
    void apalacheTypeChecksVariantReadsOfNames(@TempDir Path directory) throws Exception {
        assertTypeChecks(directory, GeneratedSpecSamples::readsVariantName, 0x7A61L);
    }

    private void assertTypeChecks(Path directory, Predicate<GeneratedSpec> shape, long seed) throws Exception {
        var samples = GeneratedSpecSamples.collect(IrGenerationConfig.defaults(), seed, 4000, 4, shape);
        assertEquals(4, samples.size(), "too few modules with the shape were generated");
        var scratch = Files.createDirectory(directory.resolve("scratch"));
        try (var worker = ApalacheProcess.start(ApalacheDistribution.locate(), scratch, CONFIG, TIMEOUT)) {
            for (var spec : samples) {
                var artifact = SpecArtifact.fromGeneratedSpec(spec, OperatorLibrary.empty());
                var result = worker.check(new ToolInput(ApalacheIrJson.render(artifact.module()), artifact.request()));
                assertNotEquals(StageOutcome.CRASH, result.outcome(), result.diagnostic());
                assertNotEquals(Optional.of(CheckerFailureCode.TYPECHECK), result.failureCode(), result.diagnostic());
            }
        }
    }
}
