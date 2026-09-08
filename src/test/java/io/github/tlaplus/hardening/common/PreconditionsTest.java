package io.github.tlaplus.hardening.common;

import static org.junit.jupiter.api.Assertions.*;

import io.github.tlaplus.hardening.config.PbtConfig;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.corpus.StageMetadata;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Draw;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PreconditionsTest {
    @Test
    void preservesNumericBoundariesIncludingNegativeZeroAndNonfiniteValues() {
        for (var value : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -1.0}) {
            assertEquals("richness must be finite and nonnegative", assertThrows(IllegalArgumentException.class,
                    () -> new GenerationMetadata(0, value)).getMessage());
        }
        for (var value : new double[] {0.0, -0.0, Double.MIN_VALUE, Double.MAX_VALUE}) {
            assertEquals(Double.doubleToLongBits(value),
                    Double.doubleToLongBits(new GenerationMetadata(0, value).richness()));
        }
        assertDoesNotThrow(() -> Preconditions.requireNonnegative(Long.MAX_VALUE, "value"));
        assertEquals("value must be nonnegative", assertThrows(IllegalArgumentException.class,
                () -> Preconditions.requireNonnegative(Long.MIN_VALUE, "value")).getMessage());
        assertEquals("value must be positive", assertThrows(IllegalArgumentException.class,
                () -> Preconditions.requirePositive(0, "value")).getMessage());
    }

    @Test
    void preservesTheFirstFailureWhenMultipleArgumentsAreInvalid() {
        assertEquals("maximumInputBytes must be nonnegative", assertThrows(IllegalArgumentException.class,
                () -> new PbtConfig(-1, 0, Double.NaN, Double.NaN)).getMessage());
        assertEquals("cohort must be nonnegative", assertThrows(IllegalArgumentException.class,
                () -> new GenerationMetadata(-1, Double.NaN)).getMessage());
        assertEquals("stage must not be blank", assertThrows(IllegalArgumentException.class,
                () -> new StageMetadata(" ", null, null, null, null)).getMessage());
        assertEquals("failure", assertThrows(NullPointerException.class,
                () -> new StageMetadata("parser", CorpusVerdict.PASS, Instant.ofEpochSecond(2),
                        Instant.ofEpochSecond(1), null)).getMessage());
        assertEquals("endTime must not precede startTime", assertThrows(IllegalArgumentException.class,
                () -> new StageMetadata("parser", CorpusVerdict.PASS, Instant.ofEpochSecond(2),
                        Instant.ofEpochSecond(1), Optional.empty())).getMessage());
        assertEquals("elements", assertThrows(NullPointerException.class,
                () -> BasicGenerators.listOf(null, -1, -2)).getMessage());
        assertEquals("expected 0 <= minimumSize <= maximumSize", assertThrows(IllegalArgumentException.class,
                () -> BasicGenerators.byteArray(-1, -2)).getMessage());
        assertEquals("count must be positive", assertThrows(IllegalArgumentException.class,
                () -> new Draw(new byte[0]).drawIndex(0, 0)).getMessage());
    }
}
