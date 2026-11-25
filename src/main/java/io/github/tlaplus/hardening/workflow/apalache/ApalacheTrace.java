package io.github.tlaplus.hardening.workflow.apalache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;

/**
 * The counterexample Apalache wrote for one check, read once for its length and, when the request
 * asks for it, its trace (ADR 0034 §5).
 *
 * <p>Apalache writes each run under its own directory below {@code --out-dir}, and the first
 * counterexample of a run as {@code violation1.itf.json} in the Informal Trace Format. The length is
 * the number of transitions: one fewer than the trace's states.
 *
 * <p>With a temporal property, Apalache adds variables of its own, whose names begin with {@code __}:
 * {@code __InLoop} turns true in the state after the loop state, and the lasso's last state equals
 * that loop state. The recorded trace drops these variables. A trace whose last state does not
 * close its loop violates the invariant, the only one Apalache checks.
 */
final class ApalacheTrace {
    static final String TRACE_FILE = "violation1.itf.json";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;
    private static final String ENCODING_PREFIX = "__";
    private static final String IN_LOOP = "__InLoop";

    private final JsonNode states;

    private ApalacheTrace(JsonNode states) {
        this.states = states;
    }

    /**
     * Reads the counterexample below {@code outputDirectory}, or empty when there is no readable
     * trace. A missing trace does not change a verdict, so it is not an error.
     */
    static Optional<ApalacheTrace> read(Path outputDirectory) {
        try (var paths = Files.walk(outputDirectory)) {
            var trace = paths.filter(path -> path.getFileName().toString().equals(TRACE_FILE))
                    .min(Comparator.naturalOrder());
            if (trace.isEmpty()) {
                return Optional.empty();
            }
            var states = JSON.readTree(trace.get().toFile()).path("states");
            return states.isArray() && !states.isEmpty() ? Optional.of(new ApalacheTrace(states)) : Optional.empty();
        } catch (IOException | UncheckedIOException exception) {
            return Optional.empty();
        }
    }

    /** Returns the number of transitions. */
    long length() {
        return states.size() - 1L;
    }

    /** Returns the trace as the JSON a worker returns (ADR 0034 §5). */
    String recorded() {
        var root = NODES.objectNode();
        var projected = NODES.arrayNode();
        states.forEach(state -> {
            var values = NODES.objectNode();
            state.properties().forEach(field -> {
                if (!field.getKey().startsWith("#") && !field.getKey().startsWith(ENCODING_PREFIX)) {
                    values.set(field.getKey(), field.getValue());
                }
            });
            projected.add(values);
        });
        var loop = loopState();
        if (loop >= 0 && projected.get(loop).equals(projected.get(projected.size() - 1))) {
            root.put("ending", "loop").put("loop", loop);
        } else {
            root.put("ending", "invariant");
        }
        root.set("states", projected);
        try {
            return JSON.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot write a JSON tree", exception);
        }
    }

    /** Returns the index of the loop state, the one before {@code __InLoop} turns true, or -1. */
    private int loopState() {
        for (var index = 0; index < states.size(); index++) {
            if (states.get(index).path(IN_LOOP).asBoolean(false)) {
                return Math.max(index - 1, 0);
            }
        }
        return -1;
    }
}
