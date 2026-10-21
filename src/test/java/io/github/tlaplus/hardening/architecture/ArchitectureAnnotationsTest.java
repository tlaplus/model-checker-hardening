package io.github.tlaplus.hardening.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.architecture.model.ArchModel.PackageNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel.TypeNode;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * The obligations that keep the atlas's views useful (ADR 0022): every package says what it is
 * for, and has key types that say what they are for.
 */
class ArchitectureAnnotationsTest {
    @Test
    void everyPackageHasASummary() {
        var missing = AtlasFixture.model().packages().stream()
                .filter(p -> p.summary().isBlank())
                .map(PackageNode::name)
                .toList();
        assertTrue(missing.isEmpty(), () -> "packages without a package-info.java summary: " + missing);
    }

    @Test
    void everyPackageHasAKeyType() {
        var model = AtlasFixture.model();
        Set<String> withKeys = model.types().stream()
                .filter(TypeNode::key)
                .map(TypeNode::packageName)
                .collect(Collectors.toSet());
        var missing = model.packages().stream()
                .map(PackageNode::name)
                .filter(p -> !withKeys.contains(p))
                .toList();
        assertTrue(missing.isEmpty(), () -> "packages without a @Key type: " + missing);
    }

    @Test
    void everyKeyTypeHasASummaryAndARole() {
        var missing = AtlasFixture.model().types().stream()
                .filter(TypeNode::key)
                .filter(t -> t.summary().isBlank() || t.role() == null)
                .map(TypeNode::name)
                .toList();
        assertTrue(missing.isEmpty(), () -> "@Key types without Javadoc or @ArchRole: " + missing);
    }
}
