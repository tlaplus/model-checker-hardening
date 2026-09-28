package io.github.tlaplus.hardening.workflow.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import io.github.tlaplus.hardening.config.CheckerProfile;
import io.github.tlaplus.hardening.config.MetamorphicConfig;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.rewrite.MetamorphicPayload;
import io.github.tlaplus.hardening.gen.rewrite.RewriteLimits;
import io.github.tlaplus.hardening.gen.rewrite.Rewriter;
import io.github.tlaplus.hardening.workflow.library.RuleLibraryPreparation;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaModules;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Decodes metamorphic expression payloads with the shipped rules (ADR 0016 §1, §3). */
class MetamorphicDecodersTest {
    private static SpecDecoders decoders;

    @BeforeAll
    static void prepareTheShippedRules() throws Exception {
        var config = new MetamorphicConfig(Optional.of(new MetamorphicConfig.RuleModule(
                "Rewrites", List.of(Path.of("libraries/rewrites").toAbsolutePath()))), Map.of(), RewriteLimits.defaults(), MetamorphicConfig.Adoption.defaults());
        var rules = RuleLibraryPreparation.prepare(config, CheckerProfile.APALACHE.defaults()).library();
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

    @Test
    void aPayloadWithoutRewriteBytesIsRejectedAsAnIdentity() {
        var failure = assertThrows(InputRejectedException.class, () -> decoders.decode(
                new CorpusInput(InputKind.EXPRESSION, MetamorphicPayload.encode(new byte[] {1, 2, 3}, new byte[0]))));
        assertEquals("no rewrite rule applied", failure.getMessage());
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
