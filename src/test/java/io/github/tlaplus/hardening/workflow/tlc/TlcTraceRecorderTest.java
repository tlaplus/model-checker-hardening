package io.github.tlaplus.hardening.workflow.tlc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.SeedConfig;
import io.github.tlaplus.hardening.workflow.worker.CheckKind;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import io.github.tlaplus.hardening.workflow.worker.ToolResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Runs real TLC on small modules in the generated skeleton and checks the trace its worker records
 * (ADR 0034 §5): the states in order, and the ending TLC's messages imply.
 */
class TlcTraceRecorderTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final CheckerStageConfig CONFIG = new CheckerStageConfig(10, 20, 256, 1);

    @TempDir
    Path directory;

    @Test
    void recordsAnInvariantViolation() throws Exception {
        var result = check(module("x = <<>>", "x' = Append(x, step)", "Len(x) < 2", "TRUE"), false);

        assertTrace("{\"ending\":\"invariant\",\"states\":["
                        + "{\"step\":0,\"x\":[]},{\"step\":1,\"x\":[0]},{\"step\":2,\"x\":[0,1]}]}",
                result);
    }

    /** TLC writes every initial state before checking the invariant there (ADR 0034 §5). */
    @Test
    void recordsAViolationInAnInitialState() throws Exception {
        var result = check(module("x \\in {1, 2, 3}", "x' = x", "x /= 2", "TRUE"), false);

        assertTrace("{\"ending\":\"invariant\",\"states\":[{\"step\":0,\"x\":2}]}", result);
    }

    @Test
    void recordsALassoBackToAnEarlierState() throws Exception {
        var source = """
                ---- MODULE FuzzInput ----
                EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants
                VARIABLES x, step
                vars == <<x, step>>
                Init == x = 0 /\\ step = 0
                Next == x' = (x + 1) % 3 /\\ step' = 0
                Inv == TRUE
                Fairness == WF_vars(Next)
                Spec == Init /\\ [][Next]_vars /\\ Fairness
                Prop == <>(x = 5)
                Liveness == Fairness => Prop
                ====
                """;

        var result = check(source, true);

        assertTrace("{\"ending\":\"loop\",\"loop\":0,\"states\":[{\"step\":0,\"x\":0},"
                        + "{\"step\":0,\"x\":1},{\"step\":0,\"x\":2},{\"step\":0,\"x\":0}]}",
                result);
    }

    /** A stuttering lasso ends in a copy of its last state, which is its loop state. */
    @Test
    void recordsAStutteringLasso() throws Exception {
        var result = check(module("x = {}", "x' = x \\cup {step}", "TRUE", "<>(9 \\in x)"), true);

        assertEquals(StageOutcome.COUNTEREXAMPLE, result.outcome(), result.diagnostic());
        assertTrace("{\"ending\":\"loop\",\"loop\":3,\"states\":[{\"step\":0,\"x\":{\"#set\":[]}},"
                        + "{\"step\":1,\"x\":{\"#set\":[0]}},{\"step\":2,\"x\":{\"#set\":[0,1]}},"
                        + "{\"step\":3,\"x\":{\"#set\":[0,1,2]}},{\"step\":3,\"x\":{\"#set\":[0,1,2]}}]}",
                result);
    }

    /**
     * After an invariant violation, TLC still checks the property on the states it explored and
     * reports its counterexample too. Only the first counterexample is recorded.
     */
    @Test
    void recordsOnlyTheFirstOfTwoCounterexamples() throws Exception {
        var result = check(module("x = 0", "x' = x + 1", "x < 1", "[][][]FALSE"), true);

        assertTrace("{\"ending\":\"invariant\",\"states\":[{\"step\":0,\"x\":0},{\"step\":1,\"x\":1}]}",
                result);
    }

    /**
     * TLC prints the trace of an invariant violation by generating the predecessor's successors
     * again; an evaluation error in another successor cuts the trace short (tlc-024). Such a
     * counterexample is recorded without a trace.
     */
    @Test
    void recordsNoTraceThatAnEvaluationErrorCutShort() throws Exception {
        var source = """
                ---- MODULE FuzzInput ----
                EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants
                VARIABLES x, step
                vars == <<x, step>>
                RECURSIVE AllPos(_)
                AllPos(S) == S = {} \\/ (LET y == CHOOSE z \\in S : TRUE IN y > 0 /\\ AllPos(S \\ {y}))
                Init == x = 0 /\\ step = 0
                Next == step < 3 /\\ AllPos({1}) /\\ x' = x /\\ step' = step + 1
                Inv == step = 0
                Fairness == TRUE
                Spec == Init /\\ [][Next]_vars /\\ Fairness
                Prop == TRUE
                Liveness == Fairness => Prop
                ====
                """;

        var result = check(source, false);

        assertEquals(Optional.empty(), result.trace());
    }

    @Test
    void recordsNoTraceUnlessAsked() throws Exception {
        var request = new CheckRequest(3, CheckKind.INVARIANT);
        var result = TlcCheckerBackend.check(Files.createTempDirectory(directory, "scratch"), List.of(),
                new ToolInput(module("x = 0", "x' = x", "x /= 0", "TRUE"), request),
                new TlcSettings(CONFIG, SeedConfig.derive(0)), TIMEOUT);

        assertEquals(StageOutcome.COUNTEREXAMPLE, result.outcome(), result.diagnostic());
        assertEquals(Optional.empty(), result.trace());
    }

    /** Compares JSON trees, in which the order of a state's variables does not matter. */
    private static void assertTrace(String expected, ToolResult result) throws Exception {
        var json = new ObjectMapper();
        assertEquals(json.readTree(expected), json.readTree(result.trace().orElseThrow()), result.trace().get());
    }

    private ToolResult check(String source, boolean temporalProperty) throws Exception {
        var request = new CheckRequest(3, temporalProperty ? CheckKind.INVARIANT_AND_PROPERTY : CheckKind.INVARIANT)
                .withTraceRecording(true);
        var result = TlcCheckerBackend.check(Files.createTempDirectory(directory, "scratch"), List.of(),
                new ToolInput(source, request), new TlcSettings(CONFIG, SeedConfig.derive(0)), TIMEOUT);
        assertEquals(StageOutcome.COUNTEREXAMPLE, result.outcome(), result.diagnostic());
        return result;
    }

    private static String module(String init, String next, String invariant, String property) {
        return """
                ---- MODULE FuzzInput ----
                EXTENDS Integers, Sequences, FiniteSets, TLC, Apalache, Variants
                VARIABLES x, step
                vars == <<x, step>>
                Init == %s /\\ step = 0
                Next == \\/ (step < 3 /\\ (%s) /\\ step' = step + 1)
                        \\/ UNCHANGED vars
                Inv == %s
                Fairness == TRUE
                Spec == Init /\\ [][Next]_vars /\\ Fairness
                Prop == %s
                Liveness == Fairness => Prop
                ====
                """.formatted(init, next, invariant, property);
    }
}
