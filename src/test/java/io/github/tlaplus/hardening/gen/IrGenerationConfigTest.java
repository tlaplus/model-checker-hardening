package io.github.tlaplus.hardening.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IrGenerationConfigTest {
    @Test
    void exposesDocumentedDefaults() {
        assertEquals(
                new IrGenerationConfig(
                        3,
                        32,
                        128,
                        8,
                        32,
                        16,
                        Set.of(
                                ExpressionCategory.ACTION,
                                ExpressionCategory.TEMPORAL,
                                ExpressionCategory.UNBOUND,
                                ExpressionCategory.EXOTIC),
                        Map.of(ExpressionForm.NAME, 8, ExpressionForm.ENUM_SET, 16)),
                IrGenerationConfig.defaults());
    }

    @Test
    void rejectsInvalidLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(-1, 1, 1, 1, 0, 0, Set.of(), Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(0, 0, 1, 1, 0, 0, Set.of(), Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(0, 1, 0, 1, 0, 0, Set.of(), Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(0, 1, 1, 0, 0, 0, Set.of(), Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(0, 1, 1, 1, -1, 0, Set.of(), Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(0, 1, 1, 1, 0, -1, Set.of(), Map.of()));
    }

    @Test
    void formWeightsDefaultToOneAndAreValidated() {
        var config = new IrGenerationConfig(
                0, 1, 1, 1, 0, 0, Set.of(), Map.of(ExpressionForm.NAME, 8));

        assertEquals(8, config.weightOf(ExpressionForm.NAME));
        assertEquals(
                ExpressionForm.DEFAULT_WEIGHT, config.weightOf(ExpressionForm.ENUM_SET));
        assertEquals(7, config.additionalSelectionSlots());

        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(
                        0, 1, 1, 1, 0, 0, Set.of(), Map.of(ExpressionForm.NAME, 0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(
                        0,
                        1,
                        1,
                        1,
                        0,
                        0,
                        Set.of(),
                        Map.of(
                                ExpressionForm.NAME,
                                IrGenerationConfig.MAXIMUM_FORM_WEIGHT + 1)));
        assertThrows(
                NullPointerException.class,
                () -> new IrGenerationConfig(0, 1, 1, 1, 0, 0, Set.of(), null));
    }

    @Test
    void snapshotsFormWeights() {
        var weights = new EnumMap<ExpressionForm, Integer>(ExpressionForm.class);
        weights.put(ExpressionForm.NAME, 8);

        var config = new IrGenerationConfig(0, 1, 1, 1, 0, 0, Set.of(), weights);
        weights.clear();

        assertEquals(8, config.weightOf(ExpressionForm.NAME));
        assertThrows(UnsupportedOperationException.class, config.formWeights()::clear);
    }

    @Test
    void snapshotsIgnoredCategories() {
        var categories = EnumSet.of(ExpressionCategory.ACTION);

        var config = new IrGenerationConfig(0, 1, 1, 1, 0, 0, categories, Map.of());
        categories.clear();

        assertEquals(Set.of(ExpressionCategory.ACTION), config.ignoredCategories());
        assertThrows(UnsupportedOperationException.class, config.ignoredCategories()::clear);
        assertThrows(
                NullPointerException.class,
                () -> new IrGenerationConfig(0, 1, 1, 1, 0, 0, null, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(
                        0, 1, 1, 1, 0, 0, Set.of(ExpressionCategory.CORE), Map.of()));
    }
}
