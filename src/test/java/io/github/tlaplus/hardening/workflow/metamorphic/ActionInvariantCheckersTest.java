package io.github.tlaplus.hardening.workflow.metamorphic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import at.forsyte.apalache.tla.lir.TlaDecl;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheCheckerBackend;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheIrJson;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.spec.SpecText;
import io.github.tlaplus.hardening.workflow.tlc.TlcCheckerBackend;
import io.github.tlaplus.hardening.workflow.tool.ToolBackend;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs a module with an action invariant through the real TLC and Apalache workers, as the
 * metamorphic relation of ADR 0016 §3 does: TLC checks {@code [][Step]_vars} as an action property,
 * and Apalache checks {@code Step} as an action invariant (probe P2).
 */
class ActionInvariantCheckersTest {
    private static final TlaTypedScopeUncheckedBuilder BUILDER = new TlaTypedScopeUncheckedBuilder();
    private static final CheckerStageConfig SETTINGS = new CheckerStageConfig(10, 60, 1024, 1);
    private static final int STEPS = 3;
    private static final CheckRequest REQUEST = new CheckRequest(STEPS, false, true);
    private static final TlaVarDecl X = TlaDeclarations.variable("x", TlaTypes.INT);
    private static final TlaVarDecl STEP = TlaDeclarations.variable("step", TlaTypes.INT);

    @Test
    void anActionInvariantEveryTransitionSatisfiesPassesInBothCheckers(@TempDir Path directory)
            throws Exception {
        var results = checkBoth(directory, module(1));

        assertEquals(StageOutcome.PASS, results.tlc().outcome(), results.tlc().diagnostic());
        assertEquals(StageOutcome.PASS, results.apalache().outcome(), results.apalache().diagnostic());
    }

    @Test
    void aViolatedActionInvariantIsACounterexampleInBothCheckers(@TempDir Path directory)
            throws Exception {
        var results = checkBoth(directory, module(2));

        assertEquals(StageOutcome.COUNTEREXAMPLE, results.tlc().outcome(), results.tlc().diagnostic());
        assertEquals(StageOutcome.COUNTEREXAMPLE, results.apalache().outcome(), results.apalache().diagnostic());
    }

    private record Results(ToolResult tlc, ToolResult apalache) {}

    private static Results checkBoth(Path directory, TlaModule module) throws Exception {
        var tlc = new TlcCheckerBackend(SETTINGS, 1, Files.createDirectory(directory.resolve("tlc")), List.of());
        var apalache = new ApalacheCheckerBackend(SETTINGS, ApalacheDistribution.locate(),
                Files.createDirectory(directory.resolve("apalache")));
        return new Results(check(tlc, SpecText::render, module), check(apalache, ApalacheIrJson::render, module));
    }

    private static ToolResult check(ToolBackend backend, Function<TlaModule, String> render, TlaModule module)
            throws Exception {
        try (var worker = backend.startWorker()) {
            return worker.check(new ToolInput(render.apply(module), REQUEST));
        }
    }

    /**
     * {@code x} counts the steps up to the bound, and {@code Step} claims that each transition adds
     * {@code increment} to it; it holds exactly for an increment of one.
     */
    private static TlaModule module(int increment) {
        var vars = BUILDER.tuple(x(), step());
        var declarations = new ArrayList<TlaDecl>(List.of(X, STEP));
        declarations.add(BUILDER.decl(FuzzInputModule.INIT,
                BUILDER.and(BUILDER.eql(x(), integer(0)), BUILDER.eql(step(), integer(0)))));
        declarations.add(BUILDER.decl(FuzzInputModule.NEXT,
                BUILDER.or(transition(1), BUILDER.unchanged(BUILDER.tuple(x(), step())))));
        declarations.add(BUILDER.decl(FuzzInputModule.INV, BUILDER.bool(true)));
        declarations.add(BUILDER.decl(FuzzInputModule.SPEC, BUILDER.and(
                reference(FuzzInputModule.INIT),
                BUILDER.always(BUILDER.stutter(reference(FuzzInputModule.NEXT), vars)))));
        declarations.add(BUILDER.decl(FuzzInputModule.STEP,
                BUILDER.stutter(transition(increment), BUILDER.tuple(x(), step()))));
        declarations.add(BUILDER.decl(FuzzInputModule.STEP_PROPERTY,
                BUILDER.always(BUILDER.stutter(reference(FuzzInputModule.STEP), BUILDER.tuple(x(), step())))));
        return TlaModules.create(FuzzInputModule.MODULE_NAME, declarations);
    }

    private static TlaEx transition(int increment) {
        return BUILDER.and(BUILDER.lt(step(), integer(STEPS)),
                BUILDER.primeEq(x(), BUILDER.plus(x(), integer(increment))),
                BUILDER.primeEq(step(), BUILDER.plus(step(), integer(1))));
    }

    private static TlaEx reference(String name) {
        return BUILDER.operApply(BUILDER.name(name, TlaTypes.operator(TlaTypes.BOOL)));
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
