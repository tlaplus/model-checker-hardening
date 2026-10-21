package io.github.tlaplus.hardening.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.architecture.DependencyScanner.ClassEdge;
import io.github.tlaplus.hardening.common.ArchModule;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Checks the compiled main classes against the layering that {@link ArchModule} declares. */
class LayeringTest {
    private static List<ClassEdge> edges;

    @BeforeAll
    static void scan() {
        edges = DependencyScanner.scan(DependencyScanner.mainClasses());
    }

    @Test
    void jdepsReportsEdgesBetweenModules() {
        var launch = new ClassEdge(
                "io.github.tlaplus.hardening.Main", "io.github.tlaplus.hardening.cli.FuzzTlaCommand");
        assertTrue(edges.contains(launch), () -> "jdeps reported " + edges.size() + " edges");
    }

    @Test
    void everyPackageBelongsToAModule() throws IOException {
        var classes = DependencyScanner.mainClasses();
        var uncovered = new TreeSet<String>();
        try (var files = Files.walk(classes)) {
            files.filter(f -> f.toString().endsWith(".class"))
                    .map(f -> classes.relativize(f.getParent()).toString().replace(File.separatorChar, '.'))
                    .filter(p -> ArchModule.ofPackage(p).isEmpty())
                    .forEach(uncovered::add);
        }
        assertTrue(uncovered.isEmpty(), () -> "packages no ArchModule constant declares: " + uncovered);
    }

    @Test
    void everyModuleDependencyIsDeclared() {
        var violations = new LinkedHashMap<String, ClassEdge>();
        for (var edge : edges) {
            var from = module(edge.from());
            var to = module(edge.to());
            if (!from.allows(to)) {
                violations.putIfAbsent(
                        from.displayName() + " -> " + to.displayName()
                                + " is not allowed by ArchModule." + from.name(),
                        edge);
            }
        }
        assertTrue(violations.isEmpty(), () -> describe(violations));
    }

    @Test
    void everyModuleHasClasses() {
        var used = edges.stream()
                .flatMap(e -> Stream.of(e.from(), e.to()))
                .map(LayeringTest::module)
                .collect(Collectors.toSet());
        for (var module : ArchModule.values()) {
            assertTrue(used.contains(module), () -> module + " has no classes with dependencies");
        }
    }

    private static ArchModule module(String type) {
        var pkg = DependencyScanner.packageOf(type);
        return ArchModule.ofPackage(pkg)
                .orElseThrow(() -> new AssertionError("no ArchModule declares package " + pkg));
    }

    private static String describe(Map<String, ClassEdge> violations) {
        return violations.entrySet().stream()
                .map(e -> e.getKey() + ":\n  " + e.getValue().from() + " -> " + e.getValue().to())
                .collect(Collectors.joining("\n"));
    }
}
