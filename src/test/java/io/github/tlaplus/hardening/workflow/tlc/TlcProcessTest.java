package io.github.tlaplus.hardening.workflow.tlc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.config.TlcStageConfig;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tlc2.output.EC;

class TlcProcessTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final TlcStageConfig CONFIG = new TlcStageConfig(10, 10, 256, 1);

    @Test
    void checksTheFixedInitNextAndInvariantConfiguration(@TempDir Path directory)
            throws Exception {
        var scratch = Files.createDirectory(directory.resolve("scratch"));

        var pass = TlcProcess.check(scratch, source("exprValue = FALSE"), CONFIG, TIMEOUT);
        var fail = TlcProcess.check(scratch, source("exprValue # FALSE"), CONFIG, TIMEOUT);

        assertEquals(StageOutcome.PASS, pass.outcome(), pass.diagnostic());
        assertEquals(StageOutcome.FAIL, fail.outcome());
        assertTrue(fail.diagnostic().toLowerCase().contains("violat"), fail.diagnostic());
        try (var paths = Files.list(scratch)) {
            assertTrue(paths.findAny().isEmpty());
        }
    }

    @Test
    void classifiesTlcExitStatusesUsingTlcsOwnCrashTaxonomy() {
        assertEquals(
                StageOutcome.PASS,
                TlcOutcomeClassifier.classifyExitStatus(EC.ExitStatus.SUCCESS));
        assertEquals(
                StageOutcome.FAIL,
                TlcOutcomeClassifier.classifyExitStatus(EC.ExitStatus.VIOLATION_SAFETY));
        assertEquals(
                StageOutcome.FAIL,
                TlcOutcomeClassifier.classifyExitStatus(EC.ExitStatus.FAILURE_SPEC_EVAL));
        assertEquals(
                StageOutcome.FAIL,
                TlcOutcomeClassifier.classifyExitStatus(EC.ExitStatus.ERROR_SPEC_PARSE));
        assertEquals(
                StageOutcome.FAIL,
                TlcOutcomeClassifier.classifyExitStatus(EC.ExitStatus.ERROR_CONFIG_PARSE));
        assertEquals(
                StageOutcome.CRASH,
                TlcOutcomeClassifier.classifyExitStatus(EC.ExitStatus.ERROR_SYSTEM));
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

}
