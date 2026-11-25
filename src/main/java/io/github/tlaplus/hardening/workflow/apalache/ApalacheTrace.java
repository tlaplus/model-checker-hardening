package io.github.tlaplus.hardening.workflow.apalache;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.OptionalLong;

/**
 * Reads the length of the counterexample Apalache wrote for one check.
 *
 * <p>Apalache writes each run under its own directory below {@code --out-dir}, and the first
 * counterexample of a run as {@code violation1.itf.json} in the Informal Trace Format. The length is
 * the number of transitions: one fewer than the trace's states.
 */
final class ApalacheTraceLength {
    static final String TRACE_FILE = "violation1.itf.json";

    private static final ObjectMapper JSON = new ObjectMapper();

    private ApalacheTraceLength() {}

    /**
     * Returns the length of the counterexample below {@code outputDirectory}, or empty when there is
     * no readable trace. A missing length does not change a verdict, so it is not an error.
     */
    static OptionalLong read(Path outputDirectory) {
        try (var paths = Files.walk(outputDirectory)) {
            var trace = paths.filter(path -> path.getFileName().toString().equals(TRACE_FILE))
                    .min(Comparator.naturalOrder());
            if (trace.isEmpty()) {
                return OptionalLong.empty();
            }
            var states = JSON.readTree(trace.get().toFile()).path("states");
            return states.isArray() && !states.isEmpty()
                    ? OptionalLong.of(states.size() - 1L)
                    : OptionalLong.empty();
        } catch (IOException | UncheckedIOException exception) {
            return OptionalLong.empty();
        }
    }
}
