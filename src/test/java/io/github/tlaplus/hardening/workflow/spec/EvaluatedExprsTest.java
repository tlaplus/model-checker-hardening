package io.github.tlaplus.hardening.workflow.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.signature.IrExprCounts;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheIrJson;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EvaluatedExprsTest {
    private final SpecDecoders decoders = SpecDecoders.of(IrGenerationConfig.defaults());

    @Test
    void countsTheEvaluatedCodeOfTheReplayedModule() {
        var bytes = new byte[4_096];
        new Random(42).nextBytes(bytes);
        var input = new CorpusInput(InputKind.MODULE, bytes);

        var counts = new EvaluatedExprs(decoders).count(input);

        assertFalse(counts.exprs().isEmpty());
        assertTrue(counts.nodes() >= counts.exprs().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(
                IrExprCounts.evaluated(decoders.decode(input).module(), FuzzInputModule.ENTRY_POINTS),
                counts);
        assertEquals(counts, new EvaluatedExprs(decoders).count(input));
    }

    @Test
    void namesConstructsAsApalachesIrJsonDoes() throws Exception {
        var names = new HashSet<String>();
        for (var seed = 0; seed < 20; seed++) {
            var bytes = new byte[4_096];
            new Random(seed).nextBytes(bytes);
            var input = new CorpusInput(InputKind.MODULE, bytes);
            var json = new ObjectMapper().readTree(ApalacheIrJson.render(decoders.decode(input).module()));
            var counted = new EvaluatedExprs(decoders).count(input).exprs().keySet();

            var serialized = new HashSet<String>();
            collectNames(json, serialized);
            assertTrue(serialized.containsAll(counted), () -> "not in the IR JSON: " + counted);
            names.addAll(counted);
        }

        assertTrue(names.contains(IrExprCounts.LET_IN), names::toString);
        assertTrue(names.containsAll(Set.of("TlaInt", "TlaStr", "TlaBool")), names::toString);
    }

    /** Collects every {@code oper} and every {@code kind} value of the IR JSON. */
    private static void collectNames(JsonNode node, Set<String> names) {
        for (var field : List.of("oper", "kind")) {
            if (node.path(field).isTextual()) {
                names.add(node.path(field).textValue());
            }
        }
        node.forEach(child -> collectNames(child, names));
    }

    @Test
    void replaysAnExpressionInput() {
        var counts = new EvaluatedExprs(decoders).count(new CorpusInput(InputKind.EXPRESSION, new byte[0]));

        assertTrue(counts.nodes() > 0);
    }
}
