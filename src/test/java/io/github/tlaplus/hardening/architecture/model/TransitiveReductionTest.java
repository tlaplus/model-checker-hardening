package io.github.tlaplus.hardening.architecture.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.architecture.model.TransitiveReduction.Arc;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TransitiveReductionTest {
    @Test
    void findsArcsThatALongerPathImplies() {
        var arcs = List.of(new Arc<>("a", "b"), new Arc<>("b", "c"), new Arc<>("a", "c"), new Arc<>("c", "d"));
        assertEquals(Set.of(new Arc<>("a", "c")), TransitiveReduction.implied(arcs));
    }

    @Test
    void keepsBothArcsOfATwoCycle() {
        var arcs = List.of(new Arc<>("a", "b"), new Arc<>("b", "a"));
        assertEquals(Set.of(), TransitiveReduction.implied(arcs));
    }
}
