package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.gen.GraphLimits;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import org.junit.jupiter.api.Test;

class GraphReportTest {
    /** Three-node spines: N and k are fixed and draw no byte, so the first byte picks the bound 1 or 2. */
    private static final SpecDecoders DECODERS = SpecDecoders.of(
            IrGenerationConfig.defaults().withGraphLimits(new GraphLimits(3, 3, 1, 3)));

    @Test
    void rendersTheGraphTheTargetAndTheExpectedOutcomeBeforeTheModule() {
        var report = DecodedInputRenderer.render(decode(1), DecodedInputRenderer.Mode.DEFAULT);
        var lines = report.lines().toList();
        assertEquals("nodes: 3", lines.get(0));
        assertEquals("edges: 0 -> 1, 1 -> 2", lines.get(1));
        assertEquals("target: 2 (distance 2, step bound 2)", lines.get(2));
        assertEquals("expected: counterexample, trace length 2", lines.get(3));
        assertEquals("module:", lines.get(4));
        assertTrue(report.replaceAll("\\s+", " ").contains("Inv == pc /= 2"), report);
    }

    @Test
    void aTargetPastTheBoundPredictsAPass() {
        var lines = DecodedInputRenderer.render(decode(0), DecodedInputRenderer.Mode.DEFAULT).lines().toList();
        assertEquals("target: 2 (distance 2, step bound 1)", lines.get(2));
        assertEquals("expected: pass", lines.get(3));
    }

    @Test
    void theSpecificationModeRendersTheModuleAlone() {
        var specification = DecodedInputRenderer.render(decode(1), DecodedInputRenderer.Mode.SPECIFICATION);
        assertTrue(specification.startsWith("----"), specification);
    }

    private static SpecArtifact decode(int boundByte) {
        return DECODERS.decode(new CorpusInput(InputKind.GRAPH, new byte[] {(byte) boundByte}));
    }
}
