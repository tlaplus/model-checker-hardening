package io.github.tlaplus.hardening.workflow.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.junit.jupiter.api.Test;

class SpecDecodersTest {
    @Test
    void coversEveryInputKind() {
        var decoders = SpecDecoders.of(IrGenerationConfig.defaults());

        for (var kind : InputKind.values()) {
            assertNotNull(decoders.decoder(kind), kind.encodedName());
        }
    }

    @Test
    void decodesEachEntryThroughTheDecoderItsKindNames() {
        var config = IrGenerationConfig.defaults();
        var decoders = SpecDecoders.of(config);

        var expression = decoders.decode(
                new CorpusInput(InputKind.EXPRESSION, new byte[0]));
        var module = decoders.decode(new CorpusInput(InputKind.MODULE, new byte[0]));

        assertEquals(CheckRequest.invariant(0), expression.request());
        assertEquals(CheckRequest.invariant(config.modules().maximumSteps()), module.request());
        assertEquals(1, expression.generated().size());
        assertTrue(expression.standaloneExpression().isPresent());
        assertTrue(module.generated().size() > 1);
        assertTrue(module.standaloneExpression().isEmpty());
    }

    /** A graph entry assembles like a module entry and keeps its graph (ADR 0023 §2). */
    @Test
    void decodesAGraphEntryAsAModuleThatKeepsItsGraph() {
        var config = IrGenerationConfig.defaults();
        var graph = SpecDecoders.of(config).decode(new CorpusInput(InputKind.GRAPH, new byte[0]));

        // The exhausted spine has 20 nodes, target 10 and its own bound 10.
        assertEquals(CheckRequest.invariant(10), graph.request());
        assertEquals(10, graph.graph().orElseThrow().target());
        assertTrue(graph.rewrite().isEmpty());
        assertTrue(graph.standaloneExpression().isEmpty());
        assertTrue(SpecText.render(graph).contains("Node0 =="), SpecText.render(graph));
        assertTrue(SpecDecoders.of(config).decode(new CorpusInput(InputKind.MODULE, new byte[0])).graph().isEmpty());
    }

    @Test
    void replacingExpressionGenerationKeepsTheRegistryComplete() {
        var expected = new TlaTypedScopeUncheckedBuilder().bool(true);
        var decoders = SpecDecoders.of(IrGenerationConfig.defaults())
                .replacingExpressions(_ -> expected);

        var expression = decoders.decode(
                new CorpusInput(InputKind.EXPRESSION, new byte[0]));

        assertEquals(expected, expression.standaloneExpression().orElseThrow());
        assertNotNull(decoders.decoder(InputKind.MODULE));
    }
}
