package io.github.tlaplus.hardening.workflow.tlc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.SeedConfig;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tlc2.output.EC;

class TlcCheckerBackendTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final CheckerStageConfig CONFIG = new CheckerStageConfig(10, 10, 256, 1);

    /** TLC-n runs the same worker with more TLC workers (ADR 0018), so it must judge alike. */
    @ParameterizedTest(name = "{0} TLC workers")
    @ValueSource(ints = {1, 2})
    void checksTheSpecificationAgainstTheInvariant(int workers, @TempDir Path directory)
            throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));
        var config = new CheckerStageConfig(CONFIG.maximumEntries(), CONFIG.timeoutSeconds(),
                CONFIG.maximumHeapMegabytes(), workers);

        var seeds = SeedConfig.derive(0);
        var settings = new TlcSettings(config, seeds);
        var pass = TlcCheckerBackend.check(scratch, List.of(), new ToolInput(source("exprValue = FALSE"), 0), settings, TIMEOUT);
        var fail = TlcCheckerBackend.check(scratch, List.of(), new ToolInput(source("exprValue # FALSE"), 0), settings, TIMEOUT);

        assertEquals(StageOutcome.PASS, pass.outcome(), pass.diagnostic());
        assertTrue(pass.failureCode().isEmpty());
        assertTrue(fail.diagnostic().contains("with " + workers + " worker"), fail.diagnostic());
        // TLC draws neither its fingerprint polynomial nor its seed (ADR 0026).
        assertTrue(fail.diagnostic().contains("with fp " + seeds.tlcFingerprint() + " and seed " + seeds.tlcSeed()),
                fail.diagnostic());
        assertEquals(StageOutcome.COUNTEREXAMPLE, fail.outcome());
        assertTrue(fail.failureCode().isEmpty());
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
                StageOutcome.COUNTEREXAMPLE,
                null);
        assertClassification(
                EC.ExitStatus.VIOLATION_DEADLOCK,
                StageOutcome.COUNTEREXAMPLE,
                null);
        assertClassification(
                EC.ExitStatus.VIOLATION_SAFETY,
                StageOutcome.COUNTEREXAMPLE,
                null);
        assertClassification(
                EC.ExitStatus.VIOLATION_LIVENESS,
                StageOutcome.COUNTEREXAMPLE,
                null);
        assertClassification(
                EC.ExitStatus.VIOLATION_ASSERT,
                StageOutcome.COUNTEREXAMPLE,
                null);
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

        var result = TlcCheckerBackend.check(scratch, List.of(),
                new ToolInput(expressionSource("161520805147"), 0),
                new TlcSettings(CONFIG, SeedConfig.derive(0)),
                TIMEOUT);

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

        var result = TlcCheckerBackend.check(scratch, List.of(), new ToolInput(expressionSource("Head(<<>>)"), 0), new TlcSettings(CONFIG, SeedConfig.derive(0)), TIMEOUT);

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
                Fairness == TRUE
                Spec == Init /\\ [][Next]_exprValue /\\ Fairness
                Prop == TRUE
                Liveness == Fairness => Prop
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
                Fairness == TRUE
                Spec == Init /\\ [][Next]_exprValue /\\ Fairness
                Prop == TRUE
                Liveness == Fairness => Prop
                ====
                """.formatted(expression);
    }

}
