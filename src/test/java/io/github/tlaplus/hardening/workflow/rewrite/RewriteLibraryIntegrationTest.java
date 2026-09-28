package io.github.tlaplus.hardening.workflow.rewrite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.tlc.TlcCheckerBackend;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Keeps the shipped rewrite rules valid: TLC finds every rule an equivalence on small domains. */
class RewriteLibraryIntegrationTest {
    private static final Path LIBRARY = Path.of("libraries/rewrites");
    private static final Path RULES = LIBRARY.resolve("Rewrites.tla");
    private static final Path CHECK = LIBRARY.resolve("RewritesCheck.tla");
    private static final Pattern RULE = Pattern.compile("^([A-Z]\\w*)\\(.*==");
    /** Checks only the assumptions; the specification holds trivially. */
    private static final CheckRequest ASSUMPTIONS = CheckRequest.invariant(0);
    /** Also checks the action-level rules, which the check module states as StepProperty. */
    private static final CheckRequest ACTIONS = new CheckRequest(0, false, true);

    @TempDir Path directory;

    @Test
    void theSelfTestCoversEveryRule() throws Exception {
        var check = Files.readString(CHECK);
        var rules = Files.readAllLines(RULES).stream()
                .map(RULE::matcher)
                .filter(java.util.regex.Matcher::find)
                .map(matcher -> matcher.group(1))
                .toList();
        assertFalse(rules.isEmpty());
        for (var rule : rules) {
            assertTrue(check.contains(rule + "("), rule + " is missing from " + CHECK);
        }
    }

    @Test
    void tlcFindsEveryRuleValid() throws Exception {
        var text = checkModule();
        var assumptions = text.lines().filter(line -> line.startsWith("ASSUME ")).toList();
        assertFalse(assumptions.isEmpty());
        try (var worker = tlc().startWorker()) {
            // Each assertion is independent, so check it in isolation and report its rule on error.
            for (var assumption : assumptions) {
                var result = worker.check(new ToolInput(isolating(text, assumption), ASSUMPTIONS));
                assertEquals(StageOutcome.PASS, result.outcome(), assumption + "\n" + result.diagnostic());
            }
            var actions = worker.check(new ToolInput(isolating(text, null), ACTIONS));
            assertEquals(StageOutcome.PASS, actions.outcome(), actions.diagnostic());
        }
    }

    @Test
    void tlcRejectsAWrongRule() throws Exception {
        var text = checkModule();
        var wrongConstant = "ASSUME \\A x \\in Ints : x = x + 1";
        var wrongAction = text.replace(
                "StepProperty == [][UnchangedPrime(v)]_v", "StepProperty == [][(UNCHANGED v) = (v' = v + 1)]_v");
        try (var worker = tlc().startWorker()) {
            assertRejected(worker.check(new ToolInput(isolating(text + "\n", null)
                    .replace("VARIABLE v", wrongConstant + "\n\nVARIABLE v"), ASSUMPTIONS)));
            assertRejected(worker.check(new ToolInput(isolating(wrongAction, null), ACTIONS)));
        }
    }

    private static void assertRejected(ToolResult result) {
        assertNotEquals(StageOutcome.PASS, result.outcome(), result.diagnostic());
        assertEquals(StageOutcome.COUNTEREXAMPLE, result.outcome(), result.diagnostic());
    }

    /** The check module under the name the TLC worker writes it to. */
    private static String checkModule() throws Exception {
        return Files.readString(CHECK).replace("MODULE RewritesCheck", "MODULE " + FuzzInputModule.MODULE_NAME);
    }

    /** Keeps only {@code kept} of the assumptions, or none. */
    private static String isolating(String text, String kept) {
        return text.lines()
                .map(line -> line.startsWith("ASSUME ") && !line.equals(kept) ? "\\* omitted for an isolated check" : line)
                .collect(Collectors.joining("\n"));
    }

    private TlcCheckerBackend tlc() throws Exception {
        return new TlcCheckerBackend(new CheckerStageConfig(10, 120, 1024, 1), 1,
                Files.createDirectories(directory.resolve("tlc")), List.of(LIBRARY.toAbsolutePath()));
    }
}
