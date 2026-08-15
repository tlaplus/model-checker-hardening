package io.github.tlaplus.hardening.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IrGenerationConfigTest {
    @Test
    void exposesDocumentedDefaults() {
        assertEquals(
                new IrGenerationConfig(
                        3,
                        32,
                        32,
                        8,
                        32,
                        16,
                        Set.of(
                                ExpressionCategory.ACTION,
                                ExpressionCategory.TEMPORAL,
                                ExpressionCategory.UNBOUND,
                                ExpressionCategory.EXOTIC)),
                IrGenerationConfig.defaults());
    }

    @Test
    void rejectsInvalidLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(-1, 1, 1, 1, 0, 0, Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(0, 0, 1, 1, 0, 0, Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(0, 1, 0, 1, 0, 0, Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(0, 1, 1, 0, 0, 0, Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(0, 1, 1, 1, -1, 0, Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(0, 1, 1, 1, 0, -1, Set.of()));
    }

    @Test
    void snapshotsIgnoredCategories() {
        var categories = EnumSet.of(ExpressionCategory.ACTION);

        var config = new IrGenerationConfig(0, 1, 1, 1, 0, 0, categories);
        categories.clear();

        assertEquals(Set.of(ExpressionCategory.ACTION), config.ignoredCategories());
        assertThrows(UnsupportedOperationException.class, config.ignoredCategories()::clear);
        assertThrows(
                NullPointerException.class,
                () -> new IrGenerationConfig(0, 1, 1, 1, 0, 0, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new IrGenerationConfig(
                        0, 1, 1, 1, 0, 0, Set.of(ExpressionCategory.CORE)));
    }
}
