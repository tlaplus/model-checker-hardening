package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.gen.GraphLimits;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.rewrite.Orientation;
import io.github.tlaplus.hardening.gen.rewrite.Rewrite;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.spec.SpecDecoders;
import io.github.tlaplus.hardening.workflow.worker.CheckKind;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import java.util.List;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.junit.jupiter.api.Test;

class DecodedInputRendererTest {
    /** Three-node spines: N and k are fixed and draw no byte, so the first byte picks the bound 1 or 2. */
    private static final SpecDecoders DECODERS = SpecDecoders.of(
            IrGenerationConfig.defaults().withGraphLimits(new GraphLimits(3, 3, 1, 3, 2, false)));

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

    @Test
    void rendersTheOrientationTheRulesAndBothSidesOfARewrite() {
        var builder = new TlaTypedScopeUncheckedBuilder();
        var original = builder.plus(builder.integer(1), builder.integer(2));
        var rewritten = builder.plus(builder.plus(builder.integer(1), builder.integer(2)), builder.integer(0));
        var report = DecodedInputRenderer.rewrite(
                new Rewrite<>(original, rewritten, Orientation.EXPLORE_REWRITE, List.of("PlusZero")),
                CheckRequest.invariant(0));
        assertEquals(String.join(System.lineSeparator(),
                "explored: rewrite",
                "rules: PlusZero",
                "original:",
                "1 + 2",
                "rewrite:",
                "(1 + 2) + 0",
                ""), report);
    }

    /** With property runs, the report names each TLC and Apalache run (ADR 0027 §4, ADR 0029 §1). */
    @Test
    void rendersEachCheckerRunOfATemporalRelation() {
        var builder = new TlaTypedScopeUncheckedBuilder();
        var original = builder.plus(builder.integer(1), builder.integer(2));
        var report = DecodedInputRenderer.rewrite(
                new Rewrite<>(original, original, Orientation.EXPLORE_ORIGINAL, List.of("EventuallyWeaken")),
                new CheckRequest(3, CheckKind.RELATION_AND_PROPERTIES));
        assertTrue(report.contains(String.join(System.lineSeparator(),
                "rules: EventuallyWeaken",
                "tlc runs:",
                "  1. SPECIFICATION Spec, INVARIANT Inv, PROPERTY StepProperty",
                "  2. SPECIFICATION Spec, PROPERTY Prop",
                "  3. SPECIFICATION SpecC, PROPERTY PropC",
                "apalache runs:",
                "  1. check --init=Init --next=Next --inv=Inv,Step --length=3 --no-deadlock",
                "  2. check --init=Init --next=Next --temporal=Prop --length=4 --no-deadlock",
                "  3. check --init=InitC --next=NextC --temporal=PropC --length=4 --no-deadlock",
                "original:")), report);
    }

    private static SpecArtifact decode(int boundByte) {
        return DECODERS.decode(new CorpusInput(InputKind.GRAPH, new byte[] {(byte) boundByte}));
    }
}
