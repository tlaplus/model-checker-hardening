package io.github.tlaplus.hardening.workflow.spec;

import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import io.github.tlaplus.hardening.config.CheckerProfile;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.rewrite.MetamorphicPayload;
import io.github.tlaplus.hardening.gen.rewrite.RewriteLimits;
import io.github.tlaplus.hardening.gen.rewrite.Rewriter;
import io.github.tlaplus.hardening.workflow.library.RuleLibraryPreparation;
import io.github.tlaplus.hardening.workflow.library.ShippedRules;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import java.util.Random;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaModules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Decodes metamorphic expression and graph payloads with the shipped rules (ADR 0016 §1, §3). */
class MetamorphicDecodersTest {
    private static SpecDecoders decoders;

    @BeforeAll
    static void prepareTheShippedRules() throws Exception {
        var config = ShippedRules.config();
        var rules = RuleLibraryPreparation.prepare(config, CheckerProfile.APALACHE.defaults(), ApalacheDistribution.locate()).library();
        decoders = SpecDecoders.metamorphic(
                IrGenerationConfig.defaults(), new Rewriter(rules, IrGenerationConfig.defaults(), RewriteLimits.defaults()));
    }

    @Test
    void theExploredSideInitializesAndTheCheckedSideIsAsserted() {
        var random = new Random(5);
        var rewritten = 0;
        for (var sample = 0; sample < 200; sample++) {
            var base = new byte[48];
            var rewrite = new byte[16];
            random.nextBytes(base);
            random.nextBytes(rewrite);
            try {
                var artifact = decoders.decode(new CorpusInput(InputKind.EXPRESSION, MetamorphicPayload.encode(base, rewrite)));
                var pair = artifact.rewrite().orElseThrow();
                assertTrue(!pair.isIdentity());
                assertEquals(CheckRequest.invariant(0), artifact.request());
                assertEquals(pair.explored(), argument(artifact, FuzzInputModule.INIT));
                assertEquals(pair.checked(), argument(artifact, FuzzInputModule.INV));
                rewritten++;
            } catch (InputRejectedException rejected) {
                // A base where no rule applied, or one the expression decoder rejects.
            }
        }
        assertTrue(rewritten > 20, "only " + rewritten + " of 200 payloads were rewritten");
    }

    /**
     * A graph base is rewritten as the module it decodes to: the original side is the graph's
     * module, the relation checks the action invariant, and the unrewritten relation pairs that
     * module with itself.
     */
    @Test
    void aGraphBaseIsRewrittenAsItsModule() {
        var conformance = SpecDecoders.of(IrGenerationConfig.defaults());
        var random = new Random(11);
        var rewritten = 0;
        for (var sample = 0; sample < 200 && rewritten < 10; sample++) {
            var base = new byte[64];
            var rewrite = new byte[32];
            random.nextBytes(base);
            random.nextBytes(rewrite);
            var input = new CorpusInput(InputKind.GRAPH, MetamorphicPayload.encode(base, rewrite));
            try {
                var graph = conformance.decode(new CorpusInput(InputKind.GRAPH, base)).graph().orElseThrow();
                var artifact = decoders.decode(input);
                var pair = artifact.rewrite().orElseThrow();
                assertTrue(!pair.isIdentity());
                assertTrue(artifact.request().actionInvariant());
                assertTrue(artifact.graph().isEmpty());
                assertEquals(moduleText(graph.spec()), moduleText((GeneratedSpec) pair.original()));
                var identity = decoders.decodeUnrewritten(input).rewrite().orElseThrow();
                assertTrue(identity.isIdentity());
                assertEquals(moduleText(graph.spec()), moduleText((GeneratedSpec) identity.original()));
                rewritten++;
            } catch (InputRejectedException rejected) {
                // A base where no rule applied, or one the graph decoder rejects.
            }
        }
        assertEquals(10, rewritten);
    }

    private static String moduleText(GeneratedSpec spec) {
        return SpecText.render(FuzzInputModule.create(spec));
    }

    @Test
    void aPayloadWithoutRewriteBytesIsRejectedAsAnIdentity() {
        var failure = assertThrows(InputRejectedException.class, () -> decoders.decode(
                new CorpusInput(InputKind.EXPRESSION, MetamorphicPayload.encode(new byte[] {1, 2, 3}, new byte[0]))));
        assertEquals("no rewrite rule applied", failure.getMessage());
    }

    /**
     * The unrewritten relation ignores the rewrite part and pairs the base with itself, so both
     * sides assert the expression the rewriting decoder takes as its original.
     */
    @Test
    void theUnrewrittenRelationPairsTheBaseWithItself() {
        var random = new Random(7);
        var compared = 0;
        for (var sample = 0; sample < 200 && compared < 10; sample++) {
            var base = new byte[48];
            var rewrite = new byte[16];
            random.nextBytes(base);
            random.nextBytes(rewrite);
            var input = new CorpusInput(InputKind.EXPRESSION, MetamorphicPayload.encode(base, rewrite));
            try {
                var original = decoders.decode(input).rewrite().orElseThrow().original();
                var artifact = decoders.decodeUnrewritten(input);
                var pair = artifact.rewrite().orElseThrow();
                assertTrue(pair.isIdentity());
                assertEquals(original, pair.original());
                assertEquals(original, argument(artifact, FuzzInputModule.INIT));
                assertEquals(original, argument(artifact, FuzzInputModule.INV));
                compared++;
            } catch (InputRejectedException rejected) {
                // A base where no rule applied, or one the expression decoder rejects.
            }
        }
        assertEquals(10, compared);
        var identity = new CorpusInput(InputKind.EXPRESSION, MetamorphicPayload.encode(new byte[] {1, 2, 3}, new byte[0]));
        assertTrue(decoders.decodeUnrewritten(identity).rewrite().orElseThrow().isIdentity());
        assertThrows(IllegalStateException.class,
                () -> SpecDecoders.of(IrGenerationConfig.defaults()).decodeUnrewritten(identity));
    }

    /** Returns the right side of the equation {@code exprValue = e} that a definition holds. */
    private static Object argument(SpecArtifact artifact, String definition) {
        var body = TlaModules.declarations(artifact.module()).stream()
                .filter(declaration -> declaration.name().equals(definition))
                .map(declaration -> ((TlaOperDecl) declaration).body())
                .findFirst().orElseThrow();
        return TlaExpressions.arguments((OperEx) body).get(1);
    }
}
