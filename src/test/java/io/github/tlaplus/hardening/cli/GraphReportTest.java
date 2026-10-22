package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class GraphReportTest {
    /** The pinned reachable input of IrGraphGeneratorsTest: 0 -> 1, 1 -> 0, target 1. */
    private static final byte[] REACHABLE = Base64.getDecoder().decode(
            "AX5tGn3sivVUTbYOOklQyuKLkESPHHAXakK+EeL74AE1NxKHHRuI9b7A04TeV5RfuNI2nD2soVo/nUcfWhl7xyuP0pMMOum/4ceQcGZoo1og0qAM80SLEo0wA27VjJvI");

    @Test
    void rendersTheGraphTheTargetAndTheExpectedOutcomeBeforeTheModule() {
        var artifact = SpecDecoders.of(IrGenerationConfig.defaults()).decode(new CorpusInput(InputKind.GRAPH, REACHABLE));
        var report = DecodedInputRenderer.render(artifact, DecodedInputRenderer.Mode.DEFAULT);
        var lines = report.lines().toList();
        assertEquals("nodes: 2", lines.get(0));
        assertEquals("edges: 0 -> 1, 1 -> 0", lines.get(1));
        assertEquals("target: 1 (distance 1, step bound 5)", lines.get(2));
        assertEquals("expected: counterexample, trace length 1", lines.get(3));
        assertEquals("module:", lines.get(4));
        assertTrue(report.replaceAll("\\s+", " ").contains("Inv == pc /= 1"), report);
    }

    @Test
    void theExhaustedGraphPredictsAViolationOfTheInitialState() {
        var artifact = SpecDecoders.of(IrGenerationConfig.defaults()).decode(new CorpusInput(InputKind.GRAPH, new byte[0]));
        var lines = DecodedInputRenderer.render(artifact, DecodedInputRenderer.Mode.DEFAULT).lines().toList();
        assertEquals("edges: 0 -> 0", lines.get(1));
        assertEquals("expected: counterexample, trace length 0", lines.get(3));
    }

    @Test
    void theSpecificationModeRendersTheModuleAlone() {
        var artifact = SpecDecoders.of(IrGenerationConfig.defaults()).decode(new CorpusInput(InputKind.GRAPH, REACHABLE));
        var specification = DecodedInputRenderer.render(artifact, DecodedInputRenderer.Mode.SPECIFICATION);
        assertTrue(specification.startsWith("----"), specification);
    }
}
