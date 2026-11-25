package io.github.tlaplus.hardening.workflow.apalache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApalacheTraceTest {
    @TempDir
    Path out;

    @Test
    void countsTheTransitionsOfTheFirstViolation() throws Exception {
        var run = Files.createDirectories(out.resolve("FuzzInput.json").resolve("2026-09-15T10-20-01_1"));
        Files.writeString(run.resolve(ApalacheTrace.TRACE_FILE), trace(3));
        Files.writeString(run.resolve("violation2.itf.json"), trace(7));

        assertEquals(2, ApalacheTrace.read(out).orElseThrow().length());
    }

    @Test
    void reportsNoTraceWithoutAReadableOne() throws Exception {
        assertEquals(Optional.empty(), ApalacheTrace.read(out).map(ApalacheTrace::length));
        assertEquals(Optional.empty(), ApalacheTrace.read(out.resolve("missing")).map(ApalacheTrace::length));

        Files.writeString(out.resolve(ApalacheTrace.TRACE_FILE), "{\"states\": ");
        assertEquals(Optional.empty(), ApalacheTrace.read(out).map(ApalacheTrace::length));
    }

    @Test
    void recordsAFiniteTraceAsAnInvariantViolation() throws Exception {
        Files.writeString(out.resolve(ApalacheTrace.TRACE_FILE), trace(2));

        assertEquals("{\"ending\":\"invariant\",\"states\":[{\"x\":{\"#bigint\":\"0\"}},{\"x\":{\"#bigint\":\"1\"}}]}",
                ApalacheTrace.read(out).orElseThrow().recorded());
    }

    /** Probe P2 of ADR 0034: __InLoop turns true after the loop state, which the last state repeats. */
    @Test
    void recordsALassoWithoutTheVariablesOfTheTemporalEncoding() throws Exception {
        Files.writeString(out.resolve(ApalacheTrace.TRACE_FILE), """
                {"states": [
                  {"#meta": {"index": 0}, "__InLoop": false, "__saved_x": {"#bigint": "0"}, "x": {"#bigint": "0"}},
                  {"#meta": {"index": 1}, "__InLoop": true, "__saved_x": {"#bigint": "0"}, "x": {"#bigint": "1"}},
                  {"#meta": {"index": 2}, "__InLoop": true, "__saved_x": {"#bigint": "0"}, "x": {"#bigint": "0"}}]}
                """);

        var recorded = ApalacheTrace.read(out).orElseThrow().recorded();

        assertTrue(recorded.startsWith("{\"ending\":\"loop\",\"loop\":0,"), recorded);
        assertTrue(!recorded.contains("__"), recorded);
    }

    private static String trace(int states) {
        var body = new StringBuilder("{\"#meta\": {\"format\": \"ITF\"}, \"vars\": [\"x\"], \"states\": [");
        for (var index = 0; index < states; index++) {
            body.append(index == 0 ? "" : ", ")
                    .append("{\"#meta\": {\"index\": ").append(index).append("}, \"x\": {\"#bigint\": \"")
                    .append(index).append("\"}}");
        }
        return body.append("]}").toString();
    }
}
