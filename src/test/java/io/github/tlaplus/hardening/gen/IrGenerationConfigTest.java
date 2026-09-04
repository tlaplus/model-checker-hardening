package io.github.tlaplus.hardening.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.engine.ExpressionKind;
import io.github.tlaplus.hardening.gen.engine.GeneralExpressionKind;
import io.github.tlaplus.hardening.gen.engine.SetExpressionKind;
import java.util.LinkedHashMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IrGenerationConfigTest {
    private static final ExpressionLimits LIMITS = new ExpressionLimits(0, 1, 1, 1, 0, 0);

    @Test
    void exposesDocumentedDefaults() {
        assertEquals(
                new IrGenerationConfig(
                        new ExpressionLimits(3, 32, 128, 8, 32, 16),
                        new ModuleLimits(3, 2, 3, 2, 5),
                        Set.of(
                                ExpressionCategory.ACTION,
                                ExpressionCategory.TEMPORAL,
                                ExpressionCategory.UNBOUND,
                                ExpressionCategory.EXOTIC),
                        Map.of(GeneralExpressionKind.NAME, 8, SetExpressionKind.ENUM_SET, 16)),
                IrGenerationConfig.defaults());
    }

    @Test
    void rejectsInvalidExpressionLimits() {
        assertThrows(
                IllegalArgumentException.class, () -> new ExpressionLimits(-1, 1, 1, 1, 0, 0));
        assertThrows(
                IllegalArgumentException.class, () -> new ExpressionLimits(0, 0, 1, 1, 0, 0));
        assertThrows(
                IllegalArgumentException.class, () -> new ExpressionLimits(0, 1, 0, 1, 0, 0));
        assertThrows(
                IllegalArgumentException.class, () -> new ExpressionLimits(0, 1, 1, 0, 0, 0));
        assertThrows(
                IllegalArgumentException.class, () -> new ExpressionLimits(0, 1, 1, 1, -1, 0));
        assertThrows(
                IllegalArgumentException.class, () -> new ExpressionLimits(0, 1, 1, 1, 0, -1));
    }

    @Test
    void rejectsInvalidModuleLimits() {
        assertThrows(IllegalArgumentException.class, () -> new ModuleLimits(0, 0, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ModuleLimits(1, -1, 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ModuleLimits(1, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ModuleLimits(1, 0, 1, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ModuleLimits(1, 0, 1, 0, -1));
    }

    @Test
    void excludingCategoriesKeepsTheOtherSettings() {
        var defaults = IrGenerationConfig.defaults();

        var narrowed = defaults.ignoring(ExpressionCategory.SET);

        assertEquals(defaults.expressions(), narrowed.expressions());
        assertEquals(defaults.modules(), narrowed.modules());
        assertEquals(defaults.formWeights(), narrowed.formWeights());
        assertTrue(narrowed.ignoredCategories().contains(ExpressionCategory.SET));
        assertTrue(narrowed.ignoredCategories().containsAll(defaults.ignoredCategories()));
    }

    @Test
    void formWeightsDefaultToOneAndAreValidated() {
        var config = new IrGenerationConfig(
                        LIMITS, ModuleLimits.defaults(), Set.of(), Map.of(GeneralExpressionKind.NAME, 8));

        assertEquals(8, config.weightOf(GeneralExpressionKind.NAME));
        assertEquals(
                ExpressionKind.DEFAULT_WEIGHT, config.weightOf(SetExpressionKind.ENUM_SET));
        assertEquals(7, config.additionalSelectionSlots());

        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(
                        LIMITS, ModuleLimits.defaults(), Set.of(), Map.of(GeneralExpressionKind.NAME, 0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(
                        LIMITS, ModuleLimits.defaults(),
                        Set.of(),
                        Map.of(
                                GeneralExpressionKind.NAME,
                                IrGenerationConfig.MAXIMUM_FORM_WEIGHT + 1)));
        assertThrows(
                NullPointerException.class,
                () -> new IrGenerationConfig(
                        LIMITS, ModuleLimits.defaults(), Set.of(), null));
    }

    @Test
    void snapshotsFormWeights() {
        var weights = new LinkedHashMap<ExpressionKind, Integer>();
        weights.put(GeneralExpressionKind.NAME, 8);

        var config = new IrGenerationConfig(
                        LIMITS, ModuleLimits.defaults(), Set.of(), weights);
        weights.clear();

        assertEquals(8, config.weightOf(GeneralExpressionKind.NAME));
        assertThrows(UnsupportedOperationException.class, config.formWeights()::clear);
    }

    @Test
    void snapshotsIgnoredCategories() {
        var categories = EnumSet.of(ExpressionCategory.ACTION);

        var config = new IrGenerationConfig(
                        LIMITS, ModuleLimits.defaults(), categories, Map.of());
        categories.clear();

        assertEquals(Set.of(ExpressionCategory.ACTION), config.ignoredCategories());
        assertThrows(UnsupportedOperationException.class, config.ignoredCategories()::clear);
        assertThrows(
                NullPointerException.class,
                () -> new IrGenerationConfig(
                        LIMITS, ModuleLimits.defaults(), null, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(
                        LIMITS, ModuleLimits.defaults(), Set.of(ExpressionCategory.CORE), Map.of()));
    }
}
