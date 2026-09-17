package io.github.tlaplus.hardening.gen.engine;

import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.containsOperator;
import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.level;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.TlaIrTestSupport.IrLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaOperators;
import org.junit.jupiter.api.Test;

/**
 * Draws Boolean expressions over state variables in every level context, with every category but
 * {@code exotic} enabled, and checks the level of what comes out. The forms exist to be reached, so
 * each context also has to produce the operators it admits.
 */
class LevelGenerationTest {
    private static final IrGenerationConfig CONFIG =
            IrGenerationConfig.defaults().withIgnoredCategories(Set.of(ExpressionCategory.EXOTIC));

    @Test
    void aStateContextProducesOnlyStateLevelExpressions() {
        var samples = generate(LevelContext.STATE, 0x57a7eL);
        samples.forEach(expression -> assertEquals(IrLevel.STATE, level(expression)));
        assertTrue(samples.stream().anyMatch(expression -> containsOperator(expression, TlaOperators.ENABLED)));
    }

    @Test
    void anActionContextPrimesButIsNeverTemporal() {
        var samples = generate(LevelContext.ACTION, 0xac710L);
        samples.forEach(expression -> assertNotEquals(IrLevel.TEMPORAL, level(expression)));
        assertTrue(samples.stream().anyMatch(expression -> level(expression) == IrLevel.ACTION));
    }

    @Test
    void aTemporalContextNestsTemporalFormulasAsTlaPlusAllows() {
        // level() fails on an action outside [][A]_v, <><<A>>_v and fairness, and on an action
        // mixed with a temporal formula; SANY is the authority, checked by ParserProcessTest.
        var samples = generate(LevelContext.TEMPORAL, 0x7e3900L);
        samples.forEach(expression -> assertNotEquals(IrLevel.ACTION, level(expression)));
        for (var nesting : List.of(TlaOperators.EQUIV, TlaOperators.FORALL3, TlaOperators.LEADS_TO,
                TlaOperators.GLOBALLY)) {
            assertTrue(samples.stream().anyMatch(expression -> containsTemporalUnder(expression, nesting)),
                    "no temporal formula under " + nesting.name());
        }
        assertTrue(samples.stream().anyMatch(expression -> containsOperator(expression, TlaOperators.STUTTER)));
        assertTrue(samples.stream().anyMatch(expression -> containsOperator(expression, TlaOperators.NO_STUTTER)));
    }

    /** Reports whether an application of {@code operator} in the expression has a temporal operand. */
    private static boolean containsTemporalUnder(TlaEx expression, at.forsyte.apalache.tla.lir.oper.TlaOper operator) {
        var found = new java.util.concurrent.atomic.AtomicBoolean();
        org.apalache_mc.tla.jir.TlaExpressions.forEach(expression, node -> {
            if (node instanceof at.forsyte.apalache.tla.lir.OperEx application && application.oper() == operator
                    && org.apalache_mc.tla.jir.TlaExpressions.arguments(application).stream()
                            .anyMatch(argument -> level(argument) == IrLevel.TEMPORAL)) {
                found.set(true);
            }
        });
        return found.get();
    }

    private static List<TlaEx> generate(LevelContext level, long seed) {
        var random = new Random(seed);
        var variables = List.of(
                ScopedName.stateVariable("x", PrimitiveType.INT),
                ScopedName.stateVariable("b", PrimitiveType.BOOL),
                ScopedName.stateVariable("s", new SetType(PrimitiveType.INT)));
        var result = new ArrayList<TlaEx>();
        for (var sample = 0; sample < 1200; sample++) {
            var input = new byte[random.nextInt(256)];
            random.nextBytes(input);
            var context = new GenerationContext(CONFIG);
            var expressions = new IrExprGenFactory(context, new IrTypeGenFactory(context));
            try {
                result.add(new Draw(input).draw(context.withBindings(variables,
                        context.withLevel(level, expressions.mkGen(PrimitiveType.BOOL, 8)))));
            } catch (InputRejectedException rejected) {
                // Some decoded choices reach an expected dead end.
            }
        }
        return result;
    }
}
