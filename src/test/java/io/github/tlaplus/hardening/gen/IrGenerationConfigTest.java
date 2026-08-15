package io.github.tlaplus.hardening.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class IrGenerationConfigTest {
    @Test
    void exposesDocumentedDefaults() {
        assertEquals(new IrGenerationConfig(3, 32, 32, 8, 32, 16), IrGenerationConfig.defaults());
    }

    @Test
    void rejectsInvalidLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(-1, 1, 1, 1, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(0, 0, 1, 1, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(0, 1, 0, 1, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(0, 1, 1, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(0, 1, 1, 1, -1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(0, 1, 1, 1, 0, -1));
    }
}
