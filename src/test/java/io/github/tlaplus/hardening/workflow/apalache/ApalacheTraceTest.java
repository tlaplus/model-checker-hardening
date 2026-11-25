package io.github.tlaplus.hardening.workflow.apalache;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApalacheTraceLengthTest {
    @TempDir
    Path out;

    @Test
    void countsTheTransitionsOfTheFirstViolation() throws Exception {
        var run = Files.createDirectories(out.resolve("FuzzInput.json").resolve("2026-09-15T10-20-01_1"));
        Files.writeString(run.resolve(ApalacheTraceLength.TRACE_FILE), trace(3));
        Files.writeString(run.resolve("violation2.itf.json"), trace(7));

        assertEquals(OptionalLong.of(2), ApalacheTraceLength.read(out));
    }

    @Test
    void reportsNoLengthWithoutAReadableTrace() throws Exception {
        assertEquals(OptionalLong.empty(), ApalacheTraceLength.read(out));
        assertEquals(OptionalLong.empty(), ApalacheTraceLength.read(out.resolve("missing")));

        Files.writeString(out.resolve(ApalacheTraceLength.TRACE_FILE), "{\"states\": ");
        assertEquals(OptionalLong.empty(), ApalacheTraceLength.read(out));
    }

    private static String trace(int states) {
        var body = new StringBuilder("{\"#meta\": {\"format\": \"ITF\"}, \"vars\": [\"x\"], \"states\": [");
        for (var index = 0; index < states; index++) {
            body.append(index == 0 ? "" : ", ")
                    .append("{\"#meta\": {\"index\": ").append(index).append("}, \"x\": ").append(index).append('}');
        }
        return body.append("]}").toString();
    }
}
