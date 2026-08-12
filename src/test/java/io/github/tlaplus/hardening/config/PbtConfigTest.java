package io.github.tlaplus.hardening.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PbtConfigTest {
    @Test
    void exposesConservativeDefaults() {
        assertEquals(new PbtConfig(1_000, 1_024), PbtConfig.defaults());
    }

    @Test
    void validatesLimitsAndFiniteInputCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new PbtConfig(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> new PbtConfig(1, -1));
        assertThrows(IllegalArgumentException.class, () -> new PbtConfig(2, 0));
        assertThrows(IllegalArgumentException.class, () -> new PbtConfig(258, 1));

        assertEquals(new PbtConfig(0, 0), new PbtConfig(0, 0));
        assertEquals(new PbtConfig(1, 0), new PbtConfig(1, 0));
        assertEquals(new PbtConfig(257, 1), new PbtConfig(257, 1));
    }
}
