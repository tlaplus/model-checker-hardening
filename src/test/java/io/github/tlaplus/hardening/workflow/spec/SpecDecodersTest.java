package io.github.tlaplus.hardening.workflow.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.OperEx;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.MetamorphicConfig;
import io.github.tlaplus.hardening.config.RuleLibraryConfig;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.Technique;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.TlaIrTestSupport;
import io.github.tlaplus.hardening.gen.rewrite.RewriteLimits;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.library.ShippedRules;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaOperators;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    /**
     * [graph] rules guard the edges of graph entries under pbt and gbt alike, and the corpus pins
     * them; mt ignores them, since it rejects graph entries (ADR 0023 §1, §6).
     */
    @Test
    void guardRulesGuardGraphEntriesUnderBothConformanceTechniques() throws Exception {
        var input = new byte[512];
        new Random(0x5eed).nextBytes(input);
        var entry = new CorpusInput(InputKind.GRAPH, input);
        var plain = SpecDecoders.prepare(FuzzTlaConfig.defaults(), Technique.GBT);
        assertEquals("", plain.rewriteManifest());
        assertEquals(0, guards(plain.decode(entry)));

        var guarded = FuzzTlaConfig.defaults().withGraphGuards(ShippedRules.config());
        for (var technique : List.of(Technique.PBT, Technique.GBT)) {
            var decoders = SpecDecoders.prepare(guarded, technique);
            assertTrue(decoders.rewriteManifest().contains("module Rewrites"), decoders.rewriteManifest());
            assertTrue(guards(decoders.decode(entry)) > 0, SpecText.render(decoders.decode(entry)));
        }
        var metamorphic = guarded.withMetamorphic(new MetamorphicConfig(
                RuleLibraryConfig.none(), RewriteLimits.defaults(), MetamorphicConfig.Adoption.defaults()));
        var refused = assertThrows(WorkflowException.class, () -> SpecDecoders.prepare(metamorphic, Technique.MT));
        assertTrue(refused.getMessage().contains("set metamorphic.rules"), refused.getMessage());
    }

    @Test
    void aRuleModuleWithoutAGuardIsRefused(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("Actions.tla"),
                "---- MODULE Actions ----\nUnchangedPrime(x) == (UNCHANGED x) = (x' = x)\n====\n");
        var config = FuzzTlaConfig.defaults().withGraphGuards(
                RuleLibraryConfig.of(new RuleLibraryConfig.RuleModule("Actions", List.of(directory))));
        var refused = assertThrows(WorkflowException.class, () -> SpecDecoders.prepare(config, Technique.GBT));
        assertTrue(refused.getMessage().startsWith("graph.rules: no rewrite rule can guard an edge"),
                refused.getMessage());
    }

    /** Counts the conjuncts of Next's edges besides step < B, pc = u, Node<u>, pc' = v and the step update. */
    private static int guards(SpecArtifact artifact) {
        var next = artifact.graph().orElseThrow().spec().nextAction();
        var count = 0;
        for (var disjunct : TlaIrTestSupport.disjuncts(next)) {
            if (disjunct instanceof OperEx conjunction && conjunction.oper() == TlaOperators.AND) {
                count += Math.max(0, TlaExpressions.arguments(conjunction).size() - 5);
            }
        }
        return count;
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
