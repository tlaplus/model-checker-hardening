package io.github.tlaplus.hardening.workflow.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import org.junit.jupiter.api.Test;

class SpecDecodersTest {
    @Test
    void coversEveryInputKind() {
        // Callers look a kind up where they need the decoder rather than guarding each use, so a
        // kind added without a decoder must fail here rather than as a null downstream.
        var decoders = SpecDecoders.of(IrGenerationConfig.defaults());

        assertEquals(InputKind.values().length, decoders.size());
        for (var kind : InputKind.values()) {
            assertNotNull(decoders.get(kind), kind.encodedName());
        }
    }

    @Test
    void decodesEachKindToItsOwnArtifact() {
        var decoders = SpecDecoders.of(IrGenerationConfig.defaults());

        var expression = decoders.get(InputKind.EXPRESSION).generate(new byte[0]);
        var module = decoders.get(InputKind.MODULE).generate(new byte[0]);

        // An expression input has one state and asks for no transitions; a module bounds its own
        // step counter and asks for exactly that many.
        assertEquals(0, expression.length());
        assertEquals(IrGenerationConfig.defaults().maximumSteps(), module.length());
        assertEquals(1, expression.generated().size());
        assertTrue(module.generated().size() > 1);
    }

    @Test
    void theExpressionOnlyDecoderIsDeliberatelyPartial() {
        // It serves a caller that consumes expression inputs only, so it must not silently gain
        // the module decoder and hide a missing-kind failure from a caller of of().
        var decoders = SpecDecoders.fromExpressions(IrGenerators.expressions());

        assertEquals(1, decoders.size());
        assertNotNull(decoders.get(InputKind.EXPRESSION));
    }
}
