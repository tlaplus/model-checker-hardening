package io.github.tlaplus.hardening.workflow.apalache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
            pass = worker.check(source("exprValue = FALSE"));
            fail = worker.check(source("exprValue # FALSE"));
            secondPass = worker.check(source("exprValue = FALSE"));

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
