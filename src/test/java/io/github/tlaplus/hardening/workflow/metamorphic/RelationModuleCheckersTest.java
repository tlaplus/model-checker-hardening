package io.github.tlaplus.hardening.workflow.metamorphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaDecl;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.gen.GeneratedOperator;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.gen.rewrite.Orientation;
import io.github.tlaplus.hardening.gen.rewrite.Rewrite;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheCheckerBackend;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.tlc.TlcCheckerBackend;
import io.github.tlaplus.hardening.workflow.tool.ToolBackend;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs the implication relation of ADR 0016 §3 through the real TLC and Apalache workers: an
 * identity passes, and a rewrite that changes the initial states or a transition is a
 * counterexample in both orientations (a small replay of probe P1).
 */
class RelationModuleCheckersTest {
    private static final TlaTypedScopeUncheckedBuilder B = new TlaTypedScopeUncheckedBuilder();
    private static final CheckerStageConfig SETTINGS = new CheckerStageConfig(10, 60, 1024, 1);
    private static final int STEPS = 3;
    private static final TlaVarDecl X = TlaDeclarations.variable("var0", TlaTypes.INT);
    private static final TlaVarDecl STEP = TlaDeclarations.variable(GeneratedSpec.STEP_VARIABLE, TlaTypes.INT);

    @TempDir Path directory;

    @Test
    void assemblesSideDefinitionsAndTheActionInvariant() {
        var artifact = relation(module(0, 1), module(0, 1), Orientation.EXPLORE_REWRITE);
        var names = TlaModules.declarations(artifact.module()).stream().map(TlaDecl::name).toList();
        assertTrue(names.containsAll(List.of("Op1", "Op1_2", "Init1", "Init2", "Inv1", "Inv2", "A1", "A2",
                FuzzInputModule.STEP, FuzzInputModule.STEP_PROPERTY)), names.toString());
        assertTrue(artifact.request().actionInvariant());
        assertEquals(FuzzInputModule.RELATION_ENTRY_POINTS, artifact.entryPoints());
    }

    @Test
    void anIdentityPassesInBothOrientations() throws Exception {
        for (var orientation : Orientation.values()) {
            assertBoth(StageOutcome.PASS, relation(module(0, 1), module(0, 1), orientation), orientation);
        }
    }

    @Test
    void aChangedTransitionIsACounterexampleInBothOrientations() throws Exception {
        for (var orientation : Orientation.values()) {
            assertBoth(StageOutcome.COUNTEREXAMPLE, relation(module(0, 1), module(0, 2), orientation), orientation);
        }
    }

    @Test
    void aChangedInitialStateIsACounterexampleInBothOrientations() throws Exception {
        for (var orientation : Orientation.values()) {
            assertBoth(StageOutcome.COUNTEREXAMPLE, relation(module(0, 1), module(1, 1), orientation), orientation);
        }
    }

    private void assertBoth(StageOutcome expected, SpecArtifact artifact, Orientation orientation) throws Exception {
        var run = Files.createTempDirectory(directory, orientation.name());
        var tlc = new TlcCheckerBackend(SETTINGS, 1, Files.createDirectory(run.resolve("tlc")), List.of());
        var apalache = new ApalacheCheckerBackend(SETTINGS, ApalacheDistribution.locate(),
                Files.createDirectory(run.resolve("apalache")));
        for (var backend : List.<ToolBackend>of(tlc, apalache)) {
            try (var worker = backend.startWorker()) {
                var result = worker.check(new ToolInput(backend.renderer().apply(artifact), artifact.request()));
                assertEquals(expected, result.outcome(), orientation + "\n" + result.diagnostic());
            }
        }
    }

    private static SpecArtifact relation(GeneratedSpec original, GeneratedSpec rewritten, Orientation orientation) {
        return SpecArtifact.fromSpecRewrite(
                new Rewrite<>(original, rewritten, orientation, List.of("Test")), OperatorLibrary.empty());
    }

    /**
     * {@code var0} starts at {@code initial} and each step adds {@code Op1 * increment}, where the
     * auxiliary operator {@code Op1 == 1} exercises the renaming of the rewritten side.
     */
    private static GeneratedSpec module(int initial, int increment) {
        var op = B.decl("Op1", B.integer(1));
        var opCall = B.operApply(B.name("Op1", TlaTypes.operator(TlaTypes.INT)));
        var init = B.and(B.eql(x(), B.integer(initial)), B.eql(step(), B.integer(0)));
        var next = B.and(B.lt(step(), B.integer(STEPS)),
                B.primeEq(x(), B.plus(x(), B.mult(opCall, B.integer(increment)))),
                B.primeEq(step(), B.plus(step(), B.integer(1))));
        return new GeneratedSpec(List.of(X, STEP), List.<GeneratedOperator>of(new GeneratedOperator.Auxiliary(op)),
                init, next, B.bool(true), Optional.empty(), STEPS);
    }

    private static TlaEx x() {
        return B.varDeclAsNameEx(X);
    }

    private static TlaEx step() {
        return B.varDeclAsNameEx(STEP);
    }
}
