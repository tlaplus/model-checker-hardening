package io.github.tlaplus.hardening.gen;

import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.assertLabelParameters;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaEx;
import java.util.Random;
import java.util.regex.Pattern;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.apalache_mc.tla.jir.TypedParameter;
import org.junit.jupiter.api.Test;

/**
 * The TLA+ rule for a label's formal parameters, and the checker that holds the generator to it.
 *
 * <p>SANY requires a label to declare exactly the identifiers introduced by expression-level
 * binders whose scope contains it: a missing one is "must contain formal parameter" and an extra
 * one is "declares extra parameter(s)". Order is irrelevant. Every case below was confirmed
 * against the SANY in the pinned Apalache distribution. {@code ParserProcessTest} keeps that
 * agreement honest end to end; these cases pin the checker itself, so that it cannot start
 * passing vacuously.
 */
class LabelParametersTest {
    private static final Pattern ANY_LABEL = Pattern.compile("label\\d+");
    private static final Pattern PARAMETERISED_LABEL = Pattern.compile("label\\d+\\(");

    private final TlaTypedScopeUncheckedBuilder builder = new TlaTypedScopeUncheckedBuilder();

    @Test
    void aLabelUnderABinderDeclaresIt() {
        assertLabelParameters(forall("y", label(equalsOne("y"), "lab", "y")));
    }

    @Test
    void aLabelUnderABinderMayNotOmitIt() {
        assertThrows(
                AssertionError.class,
                () -> assertLabelParameters(forall("y", label(equalsOne("y"), "lab"))));
    }

    @Test
    void aLabelMayNotDeclareABinderItIsNotUnder() {
        assertThrows(
                AssertionError.class,
                () -> assertLabelParameters(label(equalsOne("y"), "lab", "y")));
    }

    @Test
    void aLabelDoesNotSeeABinderIntroducedInsideIt() {
        assertLabelParameters(forall("y", label(forall("z", equalsOne("z")), "lab", "y")));
    }

    @Test
    void aNestedLabelDoesNotRepeatTheEnclosingLabelsParameters() {
        assertLabelParameters(forall("y", label(label(equalsOne("y"), "inner"), "outer", "y")));
        assertThrows(
                AssertionError.class,
                () -> assertLabelParameters(
                        forall("y", label(label(equalsOne("y"), "inner", "y"), "outer", "y"))));
    }

    @Test
    void aLetDeclarationBodyStartsANewLabelScope() {
        // The declaration body is a definition, so a binder enclosing the LET is out of reach.
        var declaration = builder.decl("Local", label(builder.bool(true), "lab"));

        assertLabelParameters(forall("y", builder.letIn(equalsOne("y"), declaration)));
    }

    @Test
    void aLambdaBodyStartsANewLabelScopeBecauseItIsPrintedAsADefinition() {
        // PrettyWriterWithAnnotations renders a lambda as a named LET definition, and the builder
        // already produces one here, so a lambda's parameters are definition parameters and its
        // body does not see a binder enclosing the lambda.
        var lambda = builder.lambda(
                "Lambda",
                label(builder.integer(1), "lab"),
                new TypedParameter[] {
                    builder.param("accumulator", TlaTypes.INT),
                    builder.param("element", TlaTypes.INT),
                });
        var fold = builder.foldSet(lambda, builder.integer(0), builder.enumSet(builder.integer(1)));

        assertLabelParameters(forall("y", builder.eql(fold, builder.name("y", TlaTypes.INT))));
    }

    @Test
    void generatedExpressionsCarryLabelsWithAndWithoutParameters() {
        // A checker that never met a parameterised label would pass whatever the generator did.
        var random = new Random(0x1abe15eedL);
        var labels = 0;
        var parameterised = 0;
        for (var sample = 0; sample < 600; sample++) {
            var input = new byte[16 + random.nextInt(240)];
            random.nextBytes(input);
            final TlaEx expression;
            try {
                expression = IrGenerators.expressions().generate(input);
            } catch (InputRejectedException rejected) {
                continue;
            }
            assertLabelParameters(expression);
            var printed = expression.toString();
            labels += count(ANY_LABEL, printed);
            parameterised += count(PARAMETERISED_LABEL, printed);
        }
        assertTrue(parameterised > 0, "no parameterised label was generated");
        assertTrue(labels > parameterised, "no unparameterised label was generated");
    }

    private static int count(Pattern pattern, String text) {
        var matcher = pattern.matcher(text);
        var found = 0;
        while (matcher.find()) {
            found++;
        }
        return found;
    }

    private TlaEx forall(String bound, TlaEx body) {
        return builder.forall(
                builder.name(bound, TlaTypes.INT), builder.enumSet(builder.integer(1)), body);
    }

    private TlaEx equalsOne(String name) {
        return builder.eql(builder.name(name, TlaTypes.INT), builder.integer(1));
    }

    private TlaEx label(TlaEx body, String name, String... parameters) {
        var arguments = new String[parameters.length + 1];
        arguments[0] = name;
        System.arraycopy(parameters, 0, arguments, 1, parameters.length);
        return builder.label(body, arguments);
    }
}
