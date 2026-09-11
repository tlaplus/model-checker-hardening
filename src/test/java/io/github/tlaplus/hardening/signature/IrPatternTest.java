package io.github.tlaplus.hardening.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaEx;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IrPatternTest {
    private final TlaTypedScopeUncheckedBuilder builder = new TlaTypedScopeUncheckedBuilder();

    @Test
    void matchesLiteralsNamesAndPredefinedSets() throws Exception {
        assertTrue(matches("0", builder.integer(0)));
        assertFalse(matches("0", builder.integer(1)));
        assertTrue(matches("-3", builder.integer(-3)));
        assertTrue(matches("\"a\"", builder.str("a")));
        assertFalse(matches("\"a\"", builder.str("b")));
        assertTrue(matches("TRUE", builder.bool(true)));
        assertFalse(matches("TRUE", builder.bool(false)));
        assertTrue(matches("STRING", builder.stringSet()));
        assertTrue(matches("Int", builder.intSet()));
        assertTrue(matches("Nat", builder.natSet()));
        assertTrue(matches("BOOLEAN", builder.booleanSet()));
        assertFalse(matches("STRING", builder.intSet()));
        assertTrue(matches("step", builder.name("step", TlaTypes.INT)));
        assertFalse(matches("step", builder.name("other", TlaTypes.INT)));
        assertFalse(matches("step", builder.str("step")));
    }

    @Test
    void matchesApplicationsByOperatorAndArity() throws Exception {
        var modulo = builder.mod(builder.name("step", TlaTypes.INT), builder.integer(0));
        assertTrue(matches("(MOD _ 0)", modulo));
        assertTrue(matches("(MOD step 0)", modulo));
        assertFalse(matches("(MOD _ 1)", modulo));
        assertFalse(matches("(DIV _ 0)", modulo));
        assertFalse(matches("(MOD _)", modulo));
        assertTrue(matches("(MOD ...)", modulo));

        var set = builder.enumSet(builder.integer(0), builder.integer(1), builder.integer(2));
        assertTrue(matches("(SET_ENUM 0 ...)", set));
        assertTrue(matches("(SET_ENUM 0 1 2)", set));
        assertFalse(matches("(SET_ENUM 0)", set));
        assertFalse(matches("(SET_ENUM 1 ...)", set));
    }

    @Test
    void requiresEveryOccurrenceOfAMetavariableToMatchEqualExpressions() throws Exception {
        var x = builder.name("x", TlaTypes.INT);
        assertTrue(matches("(EQ ?x ?x)", builder.eql(x, builder.name("x", TlaTypes.INT))));
        assertFalse(matches("(EQ ?x ?x)", builder.eql(x, builder.name("y", TlaTypes.INT))));
        assertTrue(matches("(EQ ?x ?y)", builder.eql(x, builder.name("y", TlaTypes.INT))));
    }

    @Test
    void constrainsExpressionTypesWithConsistentlyBoundTypeVariables() throws Exception {
        var integers = builder.enumSet(builder.integer(1));
        var membership = builder.in(builder.integer(1), integers);
        assertTrue(matches("(: _ \"Set(Int)\")", integers));
        assertTrue(matches("(: _ \"Set(a)\")", integers));
        assertFalse(matches("(: _ \"Seq(a)\")", integers));
        assertTrue(matches("(SET_IN (: _ \"a\") (: _ \"Set(a)\"))", membership));
        assertFalse(matches("(SET_IN (: _ \"a\") (: _ \"Set(Set(a))\"))", membership));
    }

    @Test
    void matchesTypeVariablesStructurallyAndOpenRowsAgainstExtraFields() {
        assertTrue(typeMatches("(a -> a)", "(Int -> Int)"));
        assertFalse(typeMatches("(a -> a)", "(Int -> Str)"));
        assertTrue(typeMatches("<<a, Str>>", "<<Int, Str>>"));
        assertFalse(typeMatches("<<a, a>>", "<<Int, Str>>"));
        assertTrue(typeMatches("{ f: Int, a }", "{ f: Int, g: Str }"));
        assertTrue(typeMatches("{ f: Int, a }", "{ f: Int }"));
        assertFalse(typeMatches("{ f: Int }", "{ f: Int, g: Str }"));
        assertFalse(typeMatches("{ f: Str, a }", "{ f: Int, g: Str }"));
        assertTrue(typeMatches("Tag(Int) | a", "Tag(Int) | Other(Str)"));
        assertFalse(typeMatches("Tag(Str) | a", "Tag(Int) | Other(Str)"));
    }

    @Test
    void seesThroughLabels() throws Exception {
        var labeled = builder.plus(
                builder.integer(1),
                builder.label(builder.mod(builder.integer(1), builder.integer(0)), "lab"));

        assertTrue(matches("(PLUS _ (MOD _ 0))", labeled));
    }

    @Test
    void reportsTheOutermostFirstMatchOfEachSignatureInDatabaseOrder(@TempDir Path directory)
            throws Exception {
        var database = directory.resolve("known-defects.toml");
        Files.writeString(database, """
                [[signature]]
                id = "modulo"
                references = ["none"]
                description = "Modulo by a literal zero."
                match = ['(MOD _ 0)']

                [[signature]]
                id = "label-name"
                references = ["none"]
                description = "A label's name is not an expression."
                match = ['"lab"']

                [[signature]]
                id = "division"
                references = ["none"]
                description = "Division by a literal zero."
                match = ['(DIV _ 0)']

                [[signature]]
                id = "unreferenced-definition"
                references = ["none"]
                description = "Only a definition no root reaches has this shape."
                match = ['(MOD 3 0)']

                [[signature]]
                id = "unreferenced-let"
                references = ["none"]
                description = "A LET definition counts even when its body never uses it."
                match = ['Nat']

                [[signature]]
                id = "strings"
                references = ["none"]
                description = "The set of all strings."
                match = ['STRING']
                """);
        var first = builder.mod(builder.integer(1), builder.integer(0));
        var labeled = builder.label(builder.mod(builder.integer(2), builder.integer(0)), "lab");
        var helper = builder.div(builder.integer(1), builder.integer(0));
        var sum = builder.plus(builder.plus(first, labeled), builder.name("Helper", TlaTypes.INT));
        var module = TlaModules.create("M", List.of(
                builder.decl("Unreferenced", builder.mod(builder.integer(3), builder.integer(0))),
                builder.decl("Helper", helper),
                builder.decl("Inv", builder.eql(sum, builder.integer(0))),
                builder.decl("Local", builder.letIn(
                        builder.name("L", TlaTypes.set(TlaTypes.STRING)),
                        builder.decl("L", builder.stringSet()),
                        builder.decl("Dead", builder.natSet())))));

        var matches = KnownDefectDatabase.load(List.of(database))
                .matches(module, List.of("Inv", "Local"));

        assertEquals(
                List.of("modulo", "division", "unreferenced-let", "strings"),
                matches.stream().map(match -> match.defect().id()).toList());
        assertEquals(first, matches.get(0).witness());
        assertEquals(helper, matches.get(1).witness());
        assertEquals(builder.natSet(), matches.get(2).witness());
        assertEquals(builder.stringSet(), matches.get(3).witness());
    }

    private static boolean matches(String pattern, TlaEx expression) throws PatternException {
        return PatternParser.parse(pattern).matches(IrTree.unlabeled(expression), new Bindings());
    }

    private static boolean typeMatches(String pattern, String actual) {
        return TypePattern.parse(pattern).matches(Type1Syntax.parse(actual), new Bindings());
    }
}
