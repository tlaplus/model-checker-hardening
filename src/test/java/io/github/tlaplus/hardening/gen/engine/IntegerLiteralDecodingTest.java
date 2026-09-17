package io.github.tlaplus.hardening.gen.engine;

import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.print;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IntegerLimits;
import io.github.tlaplus.hardening.gen.IntegerLiteralMode;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntegerLiteralDecodingTest {
    @Test
    void wideLiteralsKeepThePayloadEncoding() {
        assertLiteral("0", IntegerLiteralMode.WIDE);
        assertLiteral("5", IntegerLiteralMode.WIDE, 1, 5, 0);
    }

    @Test
    void smallLiteralsCentreOnTheBaseAndEscapeToWidePayloads() {
        assertLiteral("1", IntegerLiteralMode.SMALL);
        assertLiteral("3", IntegerLiteralMode.SMALL, 0, 2);
        assertLiteral("-3", IntegerLiteralMode.SMALL, 0, 5);
        assertLiteral("5", IntegerLiteralMode.SMALL, 1, 1, 5, 0);
    }

    @Test
    void boundaryLiteralsSplitTheWideHalf() {
        assertLiteral("1", IntegerLiteralMode.BOUNDARY);
        assertLiteral("3", IntegerLiteralMode.BOUNDARY, 0, 2);
        assertLiteral("2147483647", IntegerLiteralMode.BOUNDARY, 1, 0, BoundaryInteger.INT_MAX.ordinal());
        assertLiteral("5", IntegerLiteralMode.BOUNDARY, 1, 1, 1, 5, 0);
    }

    @Test
    void theClosedIntegerTerminalIsTheBase() {
        var run = new GenerationContext(config(IntegerLiteralMode.WIDE));
        var expressions = new IrExprGenFactory(run, new IrTypeGenFactory(run));

        assertEquals("1", print(new Draw(new byte[0]).draw(expressions.closedTerminal(PrimitiveType.INT))));
    }

    @Test
    void theBoundaryTableOrderIsPinned() {
        // One byte indexes the table, so reordering it reinterprets stored inputs.
        assertEquals(List.of("-1", "0", "1", "2", "15", "16", "255", "256", "32767", "32768", "65536",
                        "46340", "46341", "1073741824", "2147483647", "-2147483648", "2147483648",
                        "-2147483649", "9223372036854775807"),
                java.util.Arrays.stream(BoundaryInteger.values()).map(value -> value.value().toString()).toList());
        var tlcRange = BigInteger.valueOf(Integer.MAX_VALUE);
        assertTrue(java.util.Arrays.stream(BoundaryInteger.values()).limit(16)
                .allMatch(value -> value.value().abs().compareTo(tlcRange.add(BigInteger.ONE)) <= 0));
    }

    private static void assertLiteral(String expected, IntegerLiteralMode mode, int... input) {
        var run = new GenerationContext(config(mode));
        var expressions = new IrExprGenFactory(run, new IrTypeGenFactory(run));
        var bytes = new byte[input.length + 1];
        for (var index = 0; index < input.length; index++) {
            bytes[index] = (byte) input[index];
        }
        bytes[input.length] = 42;
        var draw = new Draw(bytes);
        var literal = draw.draw(expressions.mkGen(IntegerExpressionKind.INTEGER_LITERAL, PrimitiveType.INT, 4));
        assertEquals(expected, print(literal));
        if (input.length > 0) {
            assertEquals(1, draw.remaining(), () -> "unexpected consumption for " + expected);
        }
    }

    private static IrGenerationConfig config(IntegerLiteralMode mode) {
        var defaults = IrGenerationConfig.defaults();
        var limits = defaults.expressions();
        return defaults.withExpressionLimits(new io.github.tlaplus.hardening.gen.ExpressionLimits(
                limits.maximumTypeDepth(), limits.maximumExpressionDepth(), limits.maximumNodes(),
                limits.collections(), limits.maximumStringBytes(), new IntegerLimits(16, 1, 4, mode)));
    }
}
