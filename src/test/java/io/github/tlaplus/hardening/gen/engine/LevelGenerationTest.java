package io.github.tlaplus.hardening.gen.engine;

import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.assertCheckableTemporal;
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
    void aTemporalContextNestsActionsOnlyWhereBothCheckersAcceptThem() {
        var samples = generate(LevelContext.TEMPORAL, 0x7e3900L);
        samples.forEach(expression -> assertCheckableTemporal(expression, true));
        assertTrue(samples.stream().anyMatch(expression -> containsOperator(expression, TlaOperators.LEADS_TO)));
        assertTrue(samples.stream().anyMatch(expression -> containsOperator(expression, TlaOperators.STUTTER)));
        assertTrue(samples.stream().anyMatch(expression -> containsOperator(expression, TlaOperators.WEAK_FAIRNESS)));
    }

    @Test
    void anActionFreeTemporalContextContainsNoAction() {
        var samples = generate(LevelContext.ACTION_FREE_TEMPORAL, 0xf4eeL);
        samples.forEach(expression -> assertCheckableTemporal(expression, false));
        assertTrue(samples.stream().anyMatch(expression -> level(expression) == IrLevel.TEMPORAL));
    }

    private static List<TlaEx> generate(LevelContext level, long seed) {
        var random = new Random(seed);
        var variables = List.of(
                ScopedName.stateVariable("x", PrimitiveType.INT),
                ScopedName.stateVariable("b", PrimitiveType.BOOL),
                ScopedName.stateVariable("s", new SetType(PrimitiveType.INT)));
        var result = new ArrayList<TlaEx>();
        for (var sample = 0; sample < 600; sample++) {
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
