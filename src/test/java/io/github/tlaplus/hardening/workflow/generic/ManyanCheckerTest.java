package io.github.tlaplus.hardening.workflow.generic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.tlaplus.hardening.checker.ExplorationCount;
import io.github.tlaplus.hardening.config.CheckerProfile;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs Manyan as {@code generic/manyan.toml} describes it, when {@code make manyan-build} has staged
 * it; skipped otherwise.
 */
class ManyanCheckerTest {
    private static final String MODULE = """
            ---- MODULE FuzzInput ----
            EXTENDS Integers, Apalache, Variants
            VARIABLE x
            Init == x = 0
            Next == x < 3 /\\ x' = x + 1
            Spec == Init /\\ [][Next]_x
            Inv == x < %d
            ====
            """;

    @Test
    void reportsAPassWithItsStatesAndACounterexample(@TempDir Path directory) throws Exception {
        var checker = TomlConfig.readGeneric(Path.of("generic", "manyan.toml"));
        assumeTrue(Files.isExecutable(checker.executable()), "make manyan-build has not staged Manyan");
        var backend = new GenericCheckerBackend(CorpusStage.GENERIC, CheckerProfile.GENERIC.defaults(), checker, directory);

        var pass = backend.startWorker().check(new ToolInput(MODULE.formatted(5), CheckRequest.invariant(3)));
        assertEquals(StageOutcome.PASS, pass.outcome());
        // x = 0..3: the count that ADR 0019 compares with TLC's.
        assertEquals(OptionalLong.of(4),
                pass.metrics().orElseThrow().count(ExplorationCount.DISTINCT_STATES));
        assertEquals(StageOutcome.COUNTEREXAMPLE,
                backend.startWorker().check(new ToolInput(MODULE.formatted(2), CheckRequest.invariant(3))).outcome());
    }
}
