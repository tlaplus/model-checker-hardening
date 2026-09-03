package io.github.tlaplus.hardening.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tlaplus.hardening.gen.engine.ExpressionKind;
import io.github.tlaplus.hardening.gen.engine.GeneralExpressionKind;
import io.github.tlaplus.hardening.gen.engine.SetExpressionKind;
import java.util.LinkedHashMap;
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
                        Map.of(GeneralExpressionKind.NAME, 8, SetExpressionKind.ENUM_SET, 16)),
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
                0, 1, 1, 1, 0, 0, Set.of(), Map.of(GeneralExpressionKind.NAME, 8));

        assertEquals(8, config.weightOf(GeneralExpressionKind.NAME));
        assertEquals(
                ExpressionKind.DEFAULT_WEIGHT, config.weightOf(SetExpressionKind.ENUM_SET));
        assertEquals(7, config.additionalSelectionSlots());

        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(
                        0, 1, 1, 1, 0, 0, Set.of(), Map.of(GeneralExpressionKind.NAME, 0)));
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
                                GeneralExpressionKind.NAME,
                                IrGenerationConfig.MAXIMUM_FORM_WEIGHT + 1)));
        assertThrows(
                NullPointerException.class,
                () -> new IrGenerationConfig(0, 1, 1, 1, 0, 0, Set.of(), null));
    }

    @Test
    void snapshotsFormWeights() {
        var weights = new LinkedHashMap<ExpressionKind, Integer>();
        weights.put(GeneralExpressionKind.NAME, 8);

        var config = new IrGenerationConfig(0, 1, 1, 1, 0, 0, Set.of(), weights);
        weights.clear();

        assertEquals(8, config.weightOf(GeneralExpressionKind.NAME));
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
