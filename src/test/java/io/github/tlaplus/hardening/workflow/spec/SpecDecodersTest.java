package io.github.tlaplus.hardening.workflow.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
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

        assertEquals(0, expression.length());
        assertEquals(config.modules().maximumSteps(), module.length());
        assertEquals(1, expression.generated().size());
        assertTrue(expression.standaloneExpression().isPresent());
        assertTrue(module.generated().size() > 1);
        assertTrue(module.standaloneExpression().isEmpty());
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
