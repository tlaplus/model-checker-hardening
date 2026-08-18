package io.github.tlaplus.hardening.workflow.apalache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.config.ApalacheStageConfig;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApalacheProcessTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(30);
    private static final ApalacheStageConfig CONFIG =
            new ApalacheStageConfig(10, 30, 512, 1);

    @Test
    void checksTheFixedInitNextAndInvariantConfiguration(@TempDir Path directory)
            throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));
        var releaseJar = ApalacheDistribution.locate();

        var pass = ApalacheProcess.check(
                releaseJar,
                scratch,
                source("exprValue = FALSE"),
                CONFIG,
                TIMEOUT);
        var fail = ApalacheProcess.check(
                releaseJar,
                scratch,
                source("exprValue # FALSE"),
                CONFIG,
                TIMEOUT);

        assertEquals(StageOutcome.PASS, pass.outcome(), pass.diagnostic());
        assertTrue(pass.failureCode().isEmpty());
        assertEquals(StageOutcome.FAIL, fail.outcome(), fail.diagnostic());
        assertEquals(
                CheckerFailureCode.COUNTEREXAMPLE,
                fail.failureCode().orElseThrow());
        try (var paths = Files.list(scratch)) {
            assertTrue(paths.findAny().isEmpty());
        }
    }

    @Test
    void classifiesApalacheExitStatuses() {
        assertClassification(0, StageOutcome.PASS, null);
        assertClassification(12, StageOutcome.FAIL, CheckerFailureCode.COUNTEREXAMPLE);
        assertClassification(75, StageOutcome.FAIL, CheckerFailureCode.SPEC_EVAL);
        assertClassification(120, StageOutcome.FAIL, CheckerFailureCode.TYPECHECK);
        assertClassification(150, StageOutcome.FAIL, CheckerFailureCode.PARSE);
        assertClassification(255, StageOutcome.CRASH, null);
    }

    @Test
    void extractsAStableFailureDetail() {
        var diagnostic = "[FuzzInput.tla:10:3-10:8]: Type mismatch in expression E@12:34:56.789";

        assertEquals(
                Optional.of("Type mismatch in expression"),
                ApalacheFailureDetail.extract(diagnostic));
    }

    private static void assertClassification(
            int exitStatus, StageOutcome outcome, CheckerFailureCode code) {
        var result = ApalacheOutcomeClassifier.classify(exitStatus, "diagnostic");

        assertEquals(outcome, result.outcome());
        assertEquals(Optional.ofNullable(code), result.failureCode());
    }

    private static String source(String invariant) {
        return """
                ---- MODULE FuzzInput ----
                EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants
                VARIABLE
                    \\* @type: Bool;
                    exprValue
                Init == exprValue = FALSE
                Next == UNCHANGED exprValue
                Inv == %s
                ====
                """.formatted(invariant);
    }
}
