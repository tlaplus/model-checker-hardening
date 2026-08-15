package io.github.tlaplus.hardening.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class PbtConfigTest {
    @Test
    void exposesDocumentedDefaults() {
        assertEquals(new PbtConfig(10_240, 10, 2.0, 1.5), PbtConfig.defaults());
    }

    @Test
    void validatesInputAndRichnessControls() {
        assertThrows(
                IllegalArgumentException.class, () -> new PbtConfig(-1, 10, 2.0, 1.5));
        assertThrows(
                IllegalArgumentException.class, () -> new PbtConfig(1, 0, 2.0, 1.5));
        assertThrows(
                IllegalArgumentException.class, () -> new PbtConfig(1, 10, 0.5, 1.5));
        assertThrows(
                IllegalArgumentException.class, () -> new PbtConfig(1, 10, 2.0, 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PbtConfig(1, 10, Double.NaN, 1.5));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PbtConfig(1, 10, 2.0, Double.POSITIVE_INFINITY));
        assertEquals(new PbtConfig(0, 1, 1.0, 1.1), new PbtConfig(0, 1, 1.0, 1.1));
    }

    @Test
    void derivesTheDefaultCohortThresholds() {
        var config = PbtConfig.defaults();

        assertEquals(0.0, config.richnessThreshold(0));
        assertEquals(1.0, config.richnessThreshold(1));
        assertEquals(1.5, config.richnessThreshold(2));
        assertEquals(2.25, config.richnessThreshold(3));
        assertEquals(25.62890625, config.richnessThreshold(9));
        assertThrows(IllegalArgumentException.class, () -> config.richnessThreshold(-1));
        assertThrows(IllegalArgumentException.class, () -> config.richnessThreshold(10));
    }
}
