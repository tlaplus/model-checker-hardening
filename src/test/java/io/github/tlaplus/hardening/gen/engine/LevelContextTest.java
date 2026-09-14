package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Pins the level tables of ADR 0007. Each row was measured against TLC and Apalache, so a change
 * here is a change of what generated modules ask the checkers.
 */
class LevelContextTest {
    @Test
    void admittedLevelsArePinned() {
        var expected = Map.of(
                LevelContext.STATE, EnumSet.of(Level.STATE),
                LevelContext.ACTION, EnumSet.of(Level.STATE, Level.ACTION),
                LevelContext.TEMPORAL, EnumSet.of(Level.STATE, Level.TEMPORAL, Level.ACTION_TEMPORAL),
                LevelContext.ACTION_FREE_TEMPORAL, EnumSet.of(Level.STATE, Level.TEMPORAL));
        for (var context : LevelContext.values()) {
            var admitted = EnumSet.noneOf(Level.class);
            for (var level : Level.values()) {
                if (context.admits(level)) {
                    admitted.add(level);
                }
            }
            assertEquals(expected.get(context), admitted, context.name());
        }
    }

    @Test
    void onlyAnActionContextSurvivesAsAValueOperand() {
        assertEquals(LevelContext.STATE, LevelContext.STATE.valueOperand());
        assertEquals(LevelContext.ACTION, LevelContext.ACTION.valueOperand());
        assertEquals(LevelContext.STATE, LevelContext.TEMPORAL.valueOperand());
        assertEquals(LevelContext.STATE, LevelContext.ACTION_FREE_TEMPORAL.valueOperand());
    }

    @Test
    void theContextIsRestoredAfterAnExceptionalExit() {
        var context = new GenerationContext(IrGenerationConfig.defaults().withIgnoredCategories(Set.of()));

        assertThrows(IllegalStateException.class, () -> new Draw(new byte[0]).draw(
                context.withLevel(LevelContext.TEMPORAL, draw -> {
                    throw new IllegalStateException("body failed");
                })));
        assertEquals(LevelContext.STATE, context.level());
    }
}
