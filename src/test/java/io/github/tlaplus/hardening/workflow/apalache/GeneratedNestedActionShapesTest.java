package io.github.tlaplus.hardening.workflow.apalache;

import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.appliesAny;
import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.actionOperators;
import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.containsOperator;
import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.disjuncts;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.ModuleLimits;
import io.github.tlaplus.hardening.gen.ActionLimits;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs generated modules whose next-state action nests {@code \/} and {@code IF-THEN-ELSE} through
 * the real Apalache worker. Apalache's transition finder is the component most likely to reject a
 * richer action shape, and no other test feeds it a whole generated module, so this is the
 * regression net for that risk. Any non-crash verdict is acceptable; a crash is not.
 */
class GeneratedNestedActionShapesTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final CheckerStageConfig CONFIG = new CheckerStageConfig(10, 30, 1024, 1);

    @Test
    void apalacheDoesNotCrashOnNestedActionShapes(@TempDir Path directory) throws Exception {
        var samples = collectNestedShapeModules(6);
        assertTrue(
                samples.size() >= 4,
                "too few nested-shape modules were generated: " + samples.size());

        assertNoCrashes(directory, samples);
    }

    @Test
    void apalacheDoesNotCrashWhenNextAppliesAnActionOperator(@TempDir Path directory)
            throws Exception {
        // One state variable makes every action operator's effect the whole state, so the shape's
        // CALL kind can apply one wherever it is drawn.
        var config = IrGenerationConfig.defaults()
                .withModuleLimits(new ModuleLimits(1, 0, new ActionLimits(3, 3, 0, 3), 5));
        var generator = IrGenerators.specs(config);
        var random = new Random(0xA9701CL);
        var samples = new ArrayList<GeneratedSpec>();
        for (var sample = 0; sample < 6000 && samples.size() < 6; sample++) {
            var input = new byte[512 + random.nextInt(1536)];
            random.nextBytes(input);
            final GeneratedSpec spec;
            try {
                spec = generator.generate(input);
            } catch (InputRejectedException rejected) {
                continue;
            }
            var names = actionOperators(spec).stream()
                    .map(operator -> operator.declaration().name())
                    .collect(java.util.stream.Collectors.toSet());
            if (!names.isEmpty() && appliesAny(spec.nextAction(), names)) {
                samples.add(spec);
            }
        }
        assertTrue(
                samples.size() >= 4,
                "too few modules applied an action operator in Next: " + samples.size());
        assertNoCrashes(directory, samples);
    }

    private void assertNoCrashes(Path directory, List<GeneratedSpec> samples) throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));
        var results = new ArrayList<ToolResult>();
        try (var worker = ApalacheProcess.start(
                ApalacheDistribution.locate(), scratch, CONFIG, TIMEOUT)) {
            for (var spec : samples) {
                var artifact = SpecArtifact.fromGeneratedSpec(spec);
                results.add(worker.check(new ToolInput(
                        ApalacheIrJson.render(artifact.module()), artifact.length())));
            }
        }

        for (var result : results) {
            assertNotEquals(
                    StageOutcome.CRASH, result.outcome(), result.diagnostic());
        }
    }

    /** Generates modules until {@code wanted} of them nest a disjunction and IF-THEN-ELSE in Next. */
    private List<GeneratedSpec> collectNestedShapeModules(int wanted) {
        var generator = IrGenerators.specs(IrGenerationConfig.defaults());
        var random = new Random(0x5EA9EL);
        var withOr = new ArrayList<GeneratedSpec>();
        var withIte = new ArrayList<GeneratedSpec>();
        for (var sample = 0; sample < 4000 && withOr.size() + withIte.size() < wanted; sample++) {
            var input = new byte[512 + random.nextInt(1536)];
            random.nextBytes(input);
            final GeneratedSpec spec;
            try {
                spec = generator.generate(input);
            } catch (InputRejectedException rejected) {
                continue;
            }
            var next = spec.nextAction();
            if (withOr.size() < (wanted + 1) / 2 && disjuncts(next).stream().anyMatch(action -> containsOperator(action, "OR"))) {
                withOr.add(spec);
            } else if (withIte.size() < wanted / 2 && containsOperator(next, "IF_THEN_ELSE")) {
                withIte.add(spec);
            }
        }
        var all = new ArrayList<>(withOr);
        all.addAll(withIte);
        return all;
    }

}
