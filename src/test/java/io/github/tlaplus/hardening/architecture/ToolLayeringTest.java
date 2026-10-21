package io.github.tlaplus.hardening.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.architecture.extract.DependencyScanner;
import io.github.tlaplus.hardening.common.ArchModule;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Checks the layering of the atlas tool itself: {@code extract} and {@code render} both depend on
 * {@code model} and not on each other, and only this package wires them together.
 */
class ToolLayeringTest {
    private static final String TOOL = ArchModule.ROOT_PACKAGE + ".architecture";
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "model", Set.of(),
            "extract", Set.of("model"),
            "render", Set.of("model"));

    @Test
    void extractAndRenderDependOnlyOnTheModel() throws URISyntaxException {
        var classes = Path.of(ToolLayeringTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var edges = DependencyScanner.scan(classes).stream()
                .filter(e -> e.from().startsWith(TOOL + ".") && e.to().startsWith(TOOL + "."))
                .toList();
        for (var part : List.of("extract", "render")) {
            assertTrue(edges.stream().anyMatch(e -> part(e.from()).equals(part) && part(e.to()).equals("model")),
                    () -> part + " has no dependency on model: the scan found " + edges.size() + " edges");
        }
        var violations = edges.stream()
                .filter(e -> {
                    var from = part(e.from());
                    var to = part(e.to());
                    return !from.equals(to) && ALLOWED.containsKey(from) && !ALLOWED.get(from).contains(to);
                })
                .map(e -> e.from() + " -> " + e.to())
                .toList();
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    /** The subpackage of the tool that declares a type, or the empty string for this package. */
    private static String part(String type) {
        var pkg = DependencyScanner.packageOf(type);
        return pkg.equals(TOOL) ? "" : pkg.substring(TOOL.length() + 1);
    }
}
