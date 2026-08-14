package io.github.tlaplus.hardening.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PbtConfigTest {
    @Test
    void exposesConservativeDefaults() {
        assertEquals(new PbtConfig(1_024), PbtConfig.defaults());
    }

    @Test
    void validatesTheInputLengthLimit() {
        assertThrows(IllegalArgumentException.class, () -> new PbtConfig(-1));
        assertEquals(new PbtConfig(0), new PbtConfig(0));
    }
}
