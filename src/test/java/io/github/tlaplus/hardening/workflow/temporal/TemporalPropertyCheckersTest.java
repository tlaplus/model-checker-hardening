package io.github.tlaplus.hardening.workflow.temporal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.GeneratedSpecSamples;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.TemporalProperty;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.signature.KnownDefectDatabase;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheCheckerBackend;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.tlc.TlcCheckerBackend;
import io.github.tlaplus.hardening.workflow.tool.ToolBackend;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs assembled modules with temporal properties through the real TLC and Apalache workers.
 *
 * <p>The hand-built modules pin the semantics ADR 0007 rests on: the stuttering disjunct makes
 * Apalache see the behaviors TLC sees, the extra unrolling step lets Apalache find a lasso that
 * closes at the step bound, and fairness makes Apalache fail rather than disagree. The generated
 * modules are a regression net for the known-defect signatures: TLC may not reject the temporal
 * formula of a generated module that no shipped signature quarantines.
 */
class TemporalPropertyCheckersTest {
    private static final TlaTypedScopeUncheckedBuilder BUILDER = new TlaTypedScopeUncheckedBuilder();
    private static final CheckerStageConfig SETTINGS = new CheckerStageConfig(10, 60, 1024, 1);
    private static final int STEPS = 3;
    private static final TlaVarDecl X = TlaDeclarations.variable("x", TlaTypes.INT);
    private static final TlaVarDecl STEP = TlaDeclarations.variable("step", TlaTypes.INT);

    @Test
    void stutteringViolatesEventuallyInBothCheckers(@TempDir Path directory) throws Exception {
        var results = checkBoth(directory, module(List.of(), BUILDER.eventually(BUILDER.eql(x(), integer(STEPS)))));

        assertEquals(StageOutcome.COUNTEREXAMPLE, results.tlc().outcome(), results.tlc().diagnostic());
        assertEquals(StageOutcome.COUNTEREXAMPLE, results.apalache().outcome(), results.apalache().diagnostic());
    }

    @Test
    void aLassoClosingAtTheStepBoundIsFoundByBothCheckers(@TempDir Path directory) throws Exception {
        // Only a behavior that reaches the bound and stutters there violates []<>(step < STEPS), so
        // Apalache needs one transition beyond the bound to find it.
        var property = BUILDER.always(BUILDER.eventually(BUILDER.lt(step(), integer(STEPS))));
        var results = checkBoth(directory, module(List.of(), property));

        assertEquals(StageOutcome.COUNTEREXAMPLE, results.tlc().outcome(), results.tlc().diagnostic());
        assertEquals(StageOutcome.COUNTEREXAMPLE, results.apalache().outcome(), results.apalache().diagnostic());
    }

    @Test
    void aHoldingPropertyPassesInBothCheckers(@TempDir Path directory) throws Exception {
        var results = checkBoth(directory, module(List.of(), BUILDER.always(BUILDER.le(x(), integer(STEPS)))));

        assertEquals(StageOutcome.PASS, results.tlc().outcome(), results.tlc().diagnostic());
        assertEquals(StageOutcome.PASS, results.apalache().outcome(), results.apalache().diagnostic());
    }

    @Test
    void fairnessHoldsInTlcAndMakesApalacheFail(@TempDir Path directory) throws Exception {
        var fairness = BUILDER.weakFair(BUILDER.tuple(x(), step()), increment());
        var results = checkBoth(directory,
                module(List.of(fairness), BUILDER.eventually(BUILDER.eql(x(), integer(STEPS)))));

        assertEquals(StageOutcome.PASS, results.tlc().outcome(), results.tlc().diagnostic());
        assertEquals(StageOutcome.FAIL, results.apalache().outcome(), results.apalache().diagnostic());
        assertEquals(Optional.of(CheckerFailureCode.SPEC_EVAL), results.apalache().failureCode());
    }

    @Test
    void tlcAcceptsTheTemporalFormulaOfEveryUnquarantinedGeneratedProperty(@TempDir Path directory)
            throws Exception {
        var config = IrGenerationConfig.defaults().withIgnoredCategories(Set.of(ExpressionCategory.EXOTIC));
        var database = KnownDefectDatabase.load(List.of(KnownDefectDatabase.SHIPPED));
        var quarantined = new int[1];
        var samples = GeneratedSpecSamples.collect(config, 0x7e39F1L, 2000, 12, spec -> {
            if (spec.property().isEmpty()) {
                return false;
            }
            var module = SpecArtifact.fromGeneratedSpec(spec, OperatorLibrary.empty()).module();
            if (!database.matches(module, FuzzInputModule.ENTRY_POINTS).isEmpty()) {
                quarantined[0]++;
                return false;
            }
            return true;
        });
        assertEquals(12, samples.size(), "too few unquarantined modules with a property were generated");
        assertTrue(quarantined[0] > 0, "no generated property matched a temporal signature");

        var tlc = new TlcCheckerBackend(SETTINGS, 1, Files.createDirectory(directory.resolve("tlc")), List.of());
        for (var spec : samples) {
            var result = check(tlc, SpecArtifact.fromGeneratedSpec(spec, OperatorLibrary.empty()));
            assertFalse(result.diagnostic().contains("TLC cannot handle the temporal formula"), result.diagnostic());
            assertFalse(result.diagnostic().contains("Temporal formulas containing actions must be of forms"),
                    result.diagnostic());
        }
    }

    private record Results(ToolResult tlc, ToolResult apalache) {}

    private static Results checkBoth(Path directory, GeneratedSpec spec) throws Exception {
        var artifact = SpecArtifact.fromGeneratedSpec(spec, OperatorLibrary.empty());
        assertTrue(artifact.request().temporalProperty());
        var tlc = new TlcCheckerBackend(SETTINGS, 1, Files.createDirectory(directory.resolve("tlc")), List.of());
        var apalache = new ApalacheCheckerBackend(SETTINGS, ApalacheDistribution.locate(),
                Files.createDirectory(directory.resolve("apalache")));
        return new Results(check(tlc, artifact), check(apalache, artifact));
    }

    private static ToolResult check(ToolBackend backend, SpecArtifact artifact) throws Exception {
        try (var worker = backend.startWorker()) {
            return worker.check(new ToolInput(backend.renderer().apply(artifact), artifact.request()));
        }
    }

    /** {@code x} counts the steps up to the bound; the property and fairness are the variables. */
    private static GeneratedSpec module(List<TlaEx> fairness, TlaEx formula) {
        var init = BUILDER.and(BUILDER.eql(x(), integer(0)), BUILDER.eql(step(), integer(0)));
        return new GeneratedSpec(List.of(X, STEP), List.of(), init, increment(), BUILDER.bool(true),
                Optional.of(new TemporalProperty(fairness, formula)), STEPS);
    }

    private static TlaEx increment() {
        return BUILDER.and(BUILDER.lt(step(), integer(STEPS)), BUILDER.primeEq(x(), BUILDER.plus(x(), integer(1))),
                BUILDER.primeEq(step(), BUILDER.plus(step(), integer(1))));
    }

    private static TlaEx x() {
        return BUILDER.varDeclAsNameEx(X);
    }

    private static TlaEx step() {
        return BUILDER.varDeclAsNameEx(STEP);
    }

    private static TlaEx integer(int value) {
        return BUILDER.integer(BigInteger.valueOf(value));
    }
}
