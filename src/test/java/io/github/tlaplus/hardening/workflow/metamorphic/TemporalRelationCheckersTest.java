package io.github.tlaplus.hardening.workflow.metamorphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.SeedConfig;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.TemporalProperty;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.gen.rewrite.Orientation;
import io.github.tlaplus.hardening.gen.rewrite.Rewrite;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.spec.PropertyRelation;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.tlc.TlcCheckerBackend;
import io.github.tlaplus.hardening.workflow.tlc.TlcSettings;
import io.github.tlaplus.hardening.workflow.worker.CheckKind;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs a temporal relation (ADR 0027 §4) through the real TLC worker: each side's property is
 * checked under that side's specification, and different verdicts are a counterexample.
 *
 * <p>{@code var0} counts from 0 to 3 under weak fairness, so {@code <>(var0 = 3)} holds; without
 * the fairness, the module may stutter forever and the property is violated.
 */
class TemporalRelationCheckersTest {
    private static final TlaTypedScopeUncheckedBuilder B = new TlaTypedScopeUncheckedBuilder();
    private static final CheckerStageConfig SETTINGS = new CheckerStageConfig(10, 60, 1024, 1);
    private static final int STEPS = 3;
    private static final TlaVarDecl X = TlaDeclarations.variable("var0", TlaTypes.INT);
    private static final TlaVarDecl STEP = TlaDeclarations.variable(GeneratedSpec.STEP_VARIABLE, TlaTypes.INT);

    @TempDir Path directory;

    @Test
    void asksForThePropertyRuns() {
        var artifact = relation(module(true, reaches()), module(true, reaches()), Orientation.EXPLORE_ORIGINAL);
        assertEquals(CheckKind.RELATION_AND_PROPERTIES, artifact.request().kind());
        assertEquals(FuzzInputModule.TEMPORAL_RELATION_ENTRY_POINTS, artifact.entryPoints());
    }

    @Test
    void anEquivalentPropertyPassesInBothOrientations() throws Exception {
        var twice = B.eventually(B.eventually(B.eql(x(), B.integer(STEPS))));
        for (var orientation : Orientation.values()) {
            var result = check(relation(module(true, reaches()), module(true, twice), orientation));
            assertEquals(StageOutcome.PASS, result.outcome(), orientation + "\n" + result.diagnostic());
        }
    }

    /** Both sides violate []<>(var0 = 0), so their verdicts agree. */
    @Test
    void equallyViolatedPropertiesPass() throws Exception {
        var never = B.always(B.eventually(B.eql(x(), B.integer(0))));
        var result = check(relation(module(true, never), module(true, never), Orientation.EXPLORE_REWRITE));
        assertEquals(StageOutcome.PASS, result.outcome(), result.diagnostic());
    }

    /** Dropping the fairness is unsound: only the side that keeps it reaches var0 = 3. */
    @Test
    void aDroppedFairnessConditionIsACounterexampleInBothOrientations() throws Exception {
        for (var orientation : Orientation.values()) {
            var result = check(relation(module(true, reaches()), module(false, reaches()), orientation));
            assertEquals(StageOutcome.COUNTEREXAMPLE, result.outcome(), orientation + "\n" + result.diagnostic());
            assertTrue(result.diagnostic().startsWith("property verdicts differ"), result.diagnostic());
        }
    }

    private ToolResult check(SpecArtifact artifact) throws Exception {
        var run = Files.createTempDirectory(directory, "run");
        var tlc = new TlcCheckerBackend(
                CorpusStage.TLC, new TlcSettings(SETTINGS, SeedConfig.derive(0)), 1, run, List.of());
        try (var worker = tlc.startWorker()) {
            return worker.check(new ToolInput(tlc.renderer().apply(artifact), artifact.request()));
        }
    }

    private static SpecArtifact relation(GeneratedSpec original, GeneratedSpec rewritten, Orientation orientation) {
        return SpecArtifact.fromSpecRewrite(new Rewrite<>(original, rewritten, orientation, List.of("Test")),
                OperatorLibrary.empty(), PropertyRelation.SEPARATE_RUNS);
    }

    private static TlaEx reaches() {
        return B.eventually(B.eql(x(), B.integer(STEPS)));
    }

    /** var0 counts from 0 to 3, weakly fair if {@code fair}, and must satisfy {@code formula}. */
    private static GeneratedSpec module(boolean fair, TlaEx formula) {
        var init = B.and(B.eql(x(), B.integer(0)), B.eql(step(), B.integer(0)));
        var next = B.and(B.lt(step(), B.integer(STEPS)), B.primeEq(x(), B.plus(x(), B.integer(1))),
                B.primeEq(step(), B.plus(step(), B.integer(1))));
        var fairness = fair ? List.of(B.weakFair(B.tuple(x(), step()), next)) : List.<TlaEx>of();
        return new GeneratedSpec(List.of(X, STEP), List.of(), init, next, B.bool(true),
                Optional.of(new TemporalProperty(fairness, formula)), STEPS);
    }

    private static TlaEx x() {
        return B.varDeclAsNameEx(X);
    }

    private static TlaEx step() {
        return B.varDeclAsNameEx(STEP);
    }
}
