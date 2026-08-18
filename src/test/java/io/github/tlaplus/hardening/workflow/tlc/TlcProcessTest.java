package io.github.tlaplus.hardening.workflow.tlc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlc2.output.EC;

class TlcProcessTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final CheckerStageConfig CONFIG = new CheckerStageConfig(10, 10, 256, 1);

    @Test
    void checksTheFixedInitNextAndInvariantConfiguration(@TempDir Path directory)
            throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        var pass = TlcProcess.check(scratch, source("exprValue = FALSE"), CONFIG, TIMEOUT);
        var fail = TlcProcess.check(scratch, source("exprValue # FALSE"), CONFIG, TIMEOUT);

        assertEquals(StageOutcome.PASS, pass.outcome(), pass.diagnostic());
        assertTrue(pass.failureCode().isEmpty());
        assertEquals(StageOutcome.FAIL, fail.outcome());
        assertEquals(
                CheckerFailureCode.COUNTEREXAMPLE,
                fail.failureCode().orElseThrow());
        assertTrue(fail.diagnostic().toLowerCase().contains("violat"), fail.diagnostic());
        try (var paths = Files.list(scratch)) {
            assertTrue(paths.findAny().isEmpty());
        }
    }

    @Test
    void classifiesTlcExitStatusesUsingTlcsOwnCrashTaxonomy() {
        assertClassification(EC.ExitStatus.SUCCESS, StageOutcome.PASS, null);
        assertClassification(
                EC.ExitStatus.VIOLATION_ASSUMPTION,
                StageOutcome.FAIL,
                CheckerFailureCode.COUNTEREXAMPLE);
        assertClassification(
                EC.ExitStatus.VIOLATION_DEADLOCK,
                StageOutcome.FAIL,
                CheckerFailureCode.COUNTEREXAMPLE);
        assertClassification(
                EC.ExitStatus.VIOLATION_SAFETY,
                StageOutcome.FAIL,
                CheckerFailureCode.COUNTEREXAMPLE);
        assertClassification(
                EC.ExitStatus.VIOLATION_LIVENESS,
                StageOutcome.FAIL,
                CheckerFailureCode.COUNTEREXAMPLE);
        assertClassification(
                EC.ExitStatus.VIOLATION_ASSERT,
                StageOutcome.FAIL,
                CheckerFailureCode.COUNTEREXAMPLE);
        assertClassification(
                EC.ExitStatus.FAILURE_SPEC_EVAL,
                StageOutcome.FAIL,
                CheckerFailureCode.SPEC_EVAL);
        assertClassification(
                EC.ExitStatus.FAILURE_SAFETY_EVAL,
                StageOutcome.FAIL,
                CheckerFailureCode.SPEC_EVAL);
        assertClassification(
                EC.ExitStatus.FAILURE_LIVENESS_EVAL,
                StageOutcome.FAIL,
                CheckerFailureCode.SPEC_EVAL);
        assertClassification(
                EC.ExitStatus.ERROR_SPEC_PARSE,
                StageOutcome.FAIL,
                CheckerFailureCode.PARSE);
        assertClassification(
                EC.ExitStatus.ERROR_CONFIG_PARSE,
                StageOutcome.FAIL,
                CheckerFailureCode.PARSE);
        assertClassification(EC.ExitStatus.ERROR_SYSTEM, StageOutcome.CRASH, null);
    }

    @Test
    void classifiesAnIntegerOutsideTlcsSupportedRangeAsAFailure(@TempDir Path directory)
            throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        var result = TlcProcess.check(
                scratch, expressionSource("161520805147"), CONFIG, TIMEOUT);

        assertEquals(StageOutcome.FAIL, result.outcome(), result.diagnostic());
        assertEquals(
                CheckerFailureCode.SPEC_EVAL,
                result.failureCode().orElseThrow());
        assertTrue(
                result.diagnostic().toLowerCase().contains("number this big"),
                result.diagnostic());
    }

    @Test
    void classifiesTheIntegerTooBigErrorCodeAsAFailure() {
        var result = TlcOutcomeClassifier.classifyErrorCode(EC.TLC_INTEGER_TOO_BIG, "detail");

        assertEquals(StageOutcome.FAIL, result.outcome());
        assertEquals(
                CheckerFailureCode.SPEC_EVAL,
                result.failureCode().orElseThrow());
        assertEquals("detail", result.diagnostic());
    }

    @Test
    void classifiesAnUndefinedHeadAsSpecificationEvaluationFailure(
            @TempDir Path directory) throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        var result = TlcProcess.check(
                scratch, expressionSource("Head(<<>>)"), CONFIG, TIMEOUT);

        assertEquals(StageOutcome.FAIL, result.outcome(), result.diagnostic());
        assertEquals(
                CheckerFailureCode.SPEC_EVAL,
                result.failureCode().orElseThrow());
        assertTrue(TlcFailureDetail.extract(result.diagnostic())
                .orElseThrow()
                .contains("empty sequence"));
    }

    private static void assertClassification(
            int exitStatus, StageOutcome expectedOutcome, CheckerFailureCode expectedCode) {
        var result = TlcOutcomeClassifier.classifyExitStatus(exitStatus, "diagnostic");

        assertEquals(expectedOutcome, result.outcome());
        assertEquals(Optional.ofNullable(expectedCode), result.failureCode());
        assertEquals("diagnostic", result.diagnostic());
    }

    private static String source(String invariant) {
        return """
                ---- MODULE FuzzInput ----
                EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants
                VARIABLE exprValue
                Init == exprValue = FALSE
                Next == UNCHANGED exprValue
                Inv == %s
                ====
                """.formatted(invariant);
    }

    private static String expressionSource(String expression) {
        return """
                ---- MODULE FuzzInput ----
                EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants
                VARIABLE exprValue
                Init == exprValue = %1$s
                Next == UNCHANGED exprValue
                Inv == exprValue = %1$s
                ====
                """.formatted(expression);
    }

}
