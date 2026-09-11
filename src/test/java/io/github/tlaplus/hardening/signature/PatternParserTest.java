package io.github.tlaplus.hardening.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;
import org.apalache_mc.tla.jir.TlaOperators;
import org.junit.jupiter.api.Test;

class PatternParserTest {
    @Test
    void parsesEveryConstruct() throws Exception {
        var wildcard = new IrPattern.Wildcard();
        var zero = new IrPattern.IntegerLiteral(BigInteger.ZERO);

        assertEquals(wildcard, PatternParser.parse("_"));
        assertEquals(new IrPattern.MetaVariable("x"), PatternParser.parse("?x"));
        assertEquals(new IrPattern.IntegerLiteral(BigInteger.valueOf(-3)), PatternParser.parse("-3"));
        assertEquals(
                new IrPattern.StringLiteral("a \"b\" \\"),
                PatternParser.parse("\"a \\\"b\\\" \\\\\""));
        assertEquals(new IrPattern.BooleanLiteral(true), PatternParser.parse("TRUE"));
        assertEquals(new IrPattern.BooleanLiteral(false), PatternParser.parse("FALSE"));
        for (var set : PredefinedSet.values()) {
            assertEquals(new IrPattern.PredefinedSetLiteral(set), PatternParser.parse(set.spelling()));
        }
        assertEquals(new IrPattern.Name("step"), PatternParser.parse("step"));
        assertEquals(
                new IrPattern.Application(TlaOperators.MOD, List.of(wildcard, zero), false),
                PatternParser.parse(" ( MOD _ 0 ) "));
        assertEquals(
                new IrPattern.Application(TlaOperators.SET_ENUM, List.of(zero), true),
                PatternParser.parse("(SET_ENUM 0 ...)"));
        assertEquals(
                new IrPattern.Application(TlaOperators.SEQ, List.of(wildcard), false),
                PatternParser.parse("(Sequences!Seq _)"));
        assertEquals(
                new IrPattern.Typed(wildcard, TypePattern.parse("Set(a)")),
                PatternParser.parse("(: _ \"Set(a)\")"));
    }

    @Test
    void reportsMalformedPatternsByColumn() {
        assertError("(MODULO _ 0)", 2, "unknown operator 'MODULO'");
        assertError("(MOD _ 0", 1, "missing ')'");
        assertError("_ _", 3, "unexpected text after the pattern");
        assertError("(MOD ... _)", 6, "'...' must end the arguments");
        assertError("...", 1, "'...' may only end an operator's arguments");
        assertError("()", 2, "expected an operator name");
        assertError(")", 1, "unexpected ')'");
        assertError("", 1, "expected a pattern");
        assertError("\"abc", 1, "unterminated string");
        assertError("\"a\\n\"", 3, "unsupported escape");
        assertError("?1x", 1, "malformed metavariable '?1x'");
        assertError("(: _)", 5, "expected a quoted type");
        assertError("(: _ \"Set(\")", 6, "malformed type");
        assertError("(: _ \"Int\" _)", 1, "a type constraint takes one pattern and one type");
        assertError("a+b", 1, "unexpected 'a+b'");
    }

    private static void assertError(String pattern, int column, String message) {
        var failure = assertThrows(PatternException.class, () -> PatternParser.parse(pattern));
        assertEquals(column, failure.column(), pattern + ": " + failure.getMessage());
        assertTrue(failure.getMessage().contains(message), pattern + ": " + failure.getMessage());
    }
}
