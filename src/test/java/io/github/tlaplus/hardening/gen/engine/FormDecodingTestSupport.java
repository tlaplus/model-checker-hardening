package io.github.tlaplus.hardening.gen.engine;

import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.print;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.List;

/**
 * Pins how one selected expression form decodes: its printed IR and how many bytes it consumes
 * from a fixed input, and the selection value that picks a nested form.
 */
final class FormDecodingTestSupport {
    private FormDecodingTestSupport() {}

    /**
     * Decodes {@code kind} at {@code type} under {@code bindings} in a fresh run, and asserts the
     * printed result and that exactly one trailing byte is left unread.
     *
     * <p>Each vector gets its own run because terminal rotation and fresh-name supplies are run
     * state and would otherwise carry over between vectors.
     */
    static void assertForm(String expected, ExpressionKind kind, IrType type,
                           List<ScopedName> bindings, int depth, int... input) {
        var run = new GenerationContext(IrGenerationConfig.defaults());
        var expressions = new IrExprGenFactory(run, new IrTypeGenFactory(run));
        var draw = new Draw(bytes(input));
        var expression = draw.draw(run.withBindings(bindings, expressions.mkGen(kind, type, depth)));
        assertEquals(expected, print(expression));
        assertEquals(1, draw.remaining(), () -> "unexpected consumption for " + expected);
    }

    /**
     * Returns the first selection slot of a form for a type under the default configuration and
     * the given scope. Slots below 256 encode as the selection bytes {@code 0, slot}.
     */
    static int firstSlot(ExpressionKind selected, IrType type, List<ScopedName> bindings) {
        var run = new GenerationContext(IrGenerationConfig.defaults());
        var expressions = new IrExprGenFactory(run, new IrTypeGenFactory(run));
        return new Draw(new byte[0]).draw(run.withBindings(bindings, ignored -> {
            var slot = 0;
            for (var kind : ExpressionKindCatalog.all()) {
                if (kind == selected) {
                    assertTrue(expressions.selectionWeight(kind, type) > 0, selected + " is not applicable");
                    assertTrue(slot < 256, "slot does not fit one byte");
                    return slot;
                }
                slot += expressions.selectionWeight(kind, type);
            }
            throw new IllegalArgumentException(selected + " is not in the catalog");
        }));
    }

    /** Converts unsigned byte values to an input array. */
    private static byte[] bytes(int... input) {
        var bytes = new byte[input.length];
        for (var index = 0; index < input.length; index++) {
            bytes[index] = (byte) input[index];
        }
        return bytes;
    }
}
