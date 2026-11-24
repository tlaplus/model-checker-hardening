package io.github.tlaplus.hardening.workflow.generic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.checker.CheckerFailureCode;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.GenericCapabilities;
import io.github.tlaplus.hardening.config.GenericCheckerConfig;
import io.github.tlaplus.hardening.config.GenericCompatibility;
import io.github.tlaplus.hardening.config.GenericErrors;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs the backend with shell scripts that stand in for a compatible checker. */
class GenericCheckerBackendTest {
    private static final ToolInput INPUT = new ToolInput("---- MODULE FuzzInput ----\n====\n", CheckRequest.invariant(1));

    @TempDir Path directory;

    @Test
    void classifiesTheExitStatus() throws Exception {
        assertEquals(StageOutcome.PASS, check("exit 0").outcome());
        assertEquals(StageOutcome.COUNTEREXAMPLE, check("exit 12").outcome());
        assertEquals(Optional.of(CheckerFailureCode.PARSE), check("exit 150").failureCode());
        var crash = check("echo boom; exit 3");
        assertEquals(StageOutcome.CRASH, crash.outcome());
        assertTrue(crash.diagnostic().contains("Stub exited with status 3"), crash.diagnostic());
        assertTrue(crash.diagnostic().contains("boom"), crash.diagnostic());
    }

    /** A tool that ends at once keeps its output: the capture is drained before it is read. */
    @Test
    void keepsTheOutputOfAToolThatEndsAtOnce() throws Exception {
        var worker = backend(stub("echo boom; exit 255"), 10).startWorker();
        for (var run = 0; run < 20; run++) {
            var result = worker.check(INPUT);
            assertTrue(result.diagnostic().contains("boom"), "run " + run + ": " + result.diagnostic());
        }
    }

    /** The working directory holds the module, its configuration and the bundled modules, and is removed after. */
    @Test
    void writesTheInputFilesAndRemovesTheWorkingDirectory() throws Exception {
        var result = check("""
                test -f "$1" && test -f "$2" && test -f "$(dirname "$1")/Apalache.tla" \
                  && test -f "$(dirname "$1")/Variants.tla" && grep -q 'INVARIANT Inv' "$2" || exit 1
                exit 0""");

        assertEquals(StageOutcome.PASS, result.outcome(), result.diagnostic());
        try (var left = Files.list(directory.resolve("scratch"))) {
            assertEquals(List.of(), left.toList());
        }
    }

    /** A tool without the Apalache capability gets no bundled modules (ADR 0033 §8). */
    @Test
    void writesNoBundledModulesForAToolWithoutTheApalacheCapability() throws Exception {
        var script = stub("""
                test -f "$1" && test ! -e "$(dirname "$1")/Apalache.tla" \
                  && test ! -e "$(dirname "$1")/Variants.tla" || exit 1
                exit 0""");
        var checker = new GenericCheckerConfig("Stub", GenericCompatibility.TLC, script, List.of("{module}", "{config}"),
                new GenericCapabilities(GenericErrors.EXACT, true, false));
        var backend = new GenericCheckerBackend(CorpusStage.GENERIC,
                new CheckerStageConfig(CheckerStageConfig.DEFAULT_MAXIMUM_ENTRIES, 10, 512, 1), checker,
                directory.resolve("scratch"));

        var result = backend.startWorker().check(INPUT);

        assertEquals(StageOutcome.PASS, result.outcome(), result.diagnostic());
    }

    /** Compared by exit status alone, an error of any status fails and a fatal signal crashes. */
    @Test
    void classifiesOnlyZeroAndNonzeroInTheNonzeroMode() throws Exception {
        var checker = new GenericCheckerConfig("Stub", GenericCompatibility.TLC, stub("exit 1"),
                List.of("{module}", "{config}"), new GenericCapabilities(GenericErrors.NONZERO, true, true));
        var backend = new GenericCheckerBackend(CorpusStage.GENERIC,
                new CheckerStageConfig(CheckerStageConfig.DEFAULT_MAXIMUM_ENTRIES, 10, 512, 1), checker,
                directory.resolve("scratch"));

        assertEquals(StageOutcome.FAIL, backend.startWorker().check(INPUT).outcome());
    }

    @Test
    void reportsATimeoutAsACrash() throws Exception {
        var result = backend(stub("echo started; sleep 30"), 1).startWorker().check(INPUT);

        assertEquals(StageOutcome.CRASH, result.outcome());
        assertTrue(result.diagnostic().startsWith("Stub timed out after PT1S"), result.diagnostic());
    }

    @Test
    void reportsAMissingExecutableAsACrash() throws Exception {
        var result = backend(directory.resolve("absent"), 10).startWorker().check(INPUT);

        assertEquals(StageOutcome.CRASH, result.outcome());
        assertTrue(result.diagnostic().contains("Stub could not be run"), result.diagnostic());
    }

    private ToolResult check(String script) throws Exception {
        return backend(stub(script), 10).startWorker().check(INPUT);
    }

    private GenericCheckerBackend backend(Path executable, int timeoutSeconds) {
        var checker = new GenericCheckerConfig("Stub", GenericCompatibility.TLC, executable,
                List.of("{module}", "{config}"));
        return new GenericCheckerBackend(CorpusStage.GENERIC,
                new CheckerStageConfig(CheckerStageConfig.DEFAULT_MAXIMUM_ENTRIES, timeoutSeconds, 512, 1), checker,
                directory.resolve("scratch"));
    }

    private Path stub(String body) throws Exception {
        var script = Files.writeString(Files.createTempFile(directory, "stub-", ".sh"), "#!/bin/sh\n" + body + "\n");
        assertTrue(script.toFile().setExecutable(true));
        return script;
    }
}
