package io.github.tlaplus.hardening.workflow.apalache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.io.json.ujsonimpl.TlaToUJson$;
import at.forsyte.apalache.tla.lir.TlaDecl;
import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.apalache_mc.tla.jir.NamedType;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import scala.jdk.javaapi.CollectionConverters;

class ApalacheProcessTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final CheckerStageConfig CONFIG =
            new CheckerStageConfig(10, 30, 512, 1);

    @Test
    void checksTheFixedInitNextAndInvariantConfiguration(@TempDir Path directory)
            throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));
        var releaseJar = ApalacheDistribution.locate();

        ToolResult pass;
        ToolResult fail;
        ToolResult secondPass;
        try (var worker = ApalacheProcess.start(releaseJar, scratch, CONFIG, TIMEOUT)) {
            pass = worker.check(specification(true));
            fail = worker.check(specification(false));
            secondPass = worker.check(specification(true));

            try (var paths = Files.walk(scratch)) {
                assertEquals(
                        1,
                        paths.filter(Files::isDirectory)
                                .filter(path -> path.getFileName()
                                        .toString()
                                        .startsWith("job-"))
                                .count());
            }
        }

        assertEquals(StageOutcome.PASS, pass.outcome(), pass.diagnostic());
        assertTrue(pass.failureCode().isEmpty());
        assertTrue(pass.diagnostic().contains("EXITCODE: OK"), pass.diagnostic());
        assertEquals(StageOutcome.FAIL, fail.outcome(), fail.diagnostic());
        assertEquals(
                CheckerFailureCode.COUNTEREXAMPLE,
                fail.failureCode().orElseThrow());
        assertTrue(fail.diagnostic().contains("EXITCODE: ERROR (12)"), fail.diagnostic());
        assertEquals(StageOutcome.PASS, secondPass.outcome(), secondPass.diagnostic());
        assertTrue(secondPass.failureCode().isEmpty());
        assertTrue(secondPass.diagnostic().contains("EXITCODE: OK"), secondPass.diagnostic());
        try (var paths = Files.list(scratch)) {
            assertTrue(paths.findAny().isEmpty());
        }
    }

    @Test
    void checksClosedEmptyCollectionAndVariantTypesFromJson(@TempDir Path directory)
            throws Exception {
        var builder = new TlaTypedScopeUncheckedBuilder();
        var variantType = TlaTypes.variant(
                new NamedType("Tag0", TlaTypes.INT),
                new NamedType("Tag1", TlaTypes.BOOL));
        var expression = builder.tuple(
                builder.emptySet(TlaTypes.INT),
                builder.emptySeq(TlaTypes.BOOL),
                builder.variant("Tag0", builder.integer(0), variantType));
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        ToolResult result;
        try (var worker = ApalacheProcess.start(
                ApalacheDistribution.locate(), scratch, CONFIG, TIMEOUT)) {
            result = worker.check(ApalacheIrJson.render(expression));
        }

        assertEquals(StageOutcome.PASS, result.outcome(), result.diagnostic());
        assertFalse(result.diagnostic().contains("Expected a non-polymorphic type"));
    }

    @Test
    void classifiesApalacheExitStatuses() {
        assertClassification(0, StageOutcome.PASS, null);
        assertClassification(12, StageOutcome.FAIL, CheckerFailureCode.COUNTEREXAMPLE);
        assertClassification(75, StageOutcome.FAIL, CheckerFailureCode.SPEC_EVAL);
        assertClassification(120, StageOutcome.FAIL, CheckerFailureCode.TYPECHECK);
        assertClassification(150, StageOutcome.FAIL, CheckerFailureCode.PARSE);
        assertClassification(255, StageOutcome.CRASH, null);
        assertClassification(
                255,
                "Input error (see the manual): Division by zero at 1 ÷ 0 E@12:34:56.789",
                StageOutcome.FAIL,
                CheckerFailureCode.SPEC_EVAL);
        assertClassification(
                255,
                "Input error (see the manual): Mod by zero at 1 % 0 E@12:34:56.789",
                StageOutcome.FAIL,
                CheckerFailureCode.SPEC_EVAL);
        assertClassification(
                255,
                "Input error (see the manual): 0 ^ 0 is undefined E@12:34:56.789",
                StageOutcome.FAIL,
                CheckerFailureCode.SPEC_EVAL);
        assertClassification(
                255,
                "Input error (see the manual): Unsupported arithmetic",
                StageOutcome.CRASH,
                null);
        assertClassification(
                255,
                "Input error (see the manual): Division by zero-ish",
                StageOutcome.CRASH,
                null);
    }

    @Test
    void extractsAStableFailureDetail() {
        var diagnostic = "[FuzzInput.json:10:3-10:8]: Type mismatch in expression E@12:34:56.789";

        assertEquals(
                Optional.of("Type mismatch in expression"),
                ApalacheFailureDetail.extract(diagnostic));
    }

    private static void assertClassification(
            int exitStatus, StageOutcome outcome, CheckerFailureCode code) {
        assertClassification(exitStatus, "diagnostic", outcome, code);
    }

    private static void assertClassification(
            int exitStatus, String diagnostic, StageOutcome outcome, CheckerFailureCode code) {
        var result = ApalacheOutcomeClassifier.classify(exitStatus, diagnostic);

        assertEquals(outcome, result.outcome());
        assertEquals(Optional.ofNullable(code), result.failureCode());
    }

    private static String specification(boolean invariantHolds) {
        var builder = new TlaTypedScopeUncheckedBuilder();
        var variable = TlaDeclarations.variable("exprValue", TlaTypes.BOOL);
        var name = builder.varDeclAsNameEx(variable);
        var value = builder.bool(false);
        var init = builder.decl("Init", builder.eql(name, value));
        var next = builder.decl("Next", builder.unchanged(name));
        var invariant = invariantHolds
                ? builder.eql(name, value)
                : builder.neql(name, value);
        var inv = builder.decl("Inv", invariant);
        var declarations = List.<TlaDecl>of(variable, init, next, inv);
        var module = new TlaModule(
                "FuzzInput", CollectionConverters.asScala(declarations).toSeq());
        return TlaToUJson$.MODULE$.apply(module).render(2, false);
    }
}
