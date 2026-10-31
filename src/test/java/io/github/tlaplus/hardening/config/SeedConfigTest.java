package io.github.tlaplus.hardening.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import tlc2.util.FP64;

class SeedConfigTest {
    /** {@code tlc_fp} is validated against TLC's table of polynomials, which the constant copies. */
    @Test
    void theFingerprintRangeIsTlcs() {
        assertEquals(FP64.Polys.length, SeedConfig.TLC_FINGERPRINT_POLYNOMIALS);
    }

    @Test
    void theTlcSeedsFollowFromTheGeneratorSeed() {
        assertEquals(SeedConfig.derive(42), SeedConfig.derive(42));
        assertEquals(42, SeedConfig.derive(42).generator());
        assertNotEquals(SeedConfig.derive(42), SeedConfig.derive(43));
    }

    @Test
    void rejectsSeedsOutsideTheirRanges() {
        assertThrows(IllegalArgumentException.class, () -> new SeedConfig(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SeedConfig(0, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new SeedConfig(0, SeedConfig.TLC_FINGERPRINT_POLYNOMIALS, 0));
    }
}
