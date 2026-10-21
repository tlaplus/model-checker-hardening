package io.github.tlaplus.hardening.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.architecture.extract.DependencyScanner.ClassEdge;
import io.github.tlaplus.hardening.architecture.extract.DependencyScanner;
import io.github.tlaplus.hardening.architecture.extract.ModuleEdges;
import io.github.tlaplus.hardening.common.ArchModule;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
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
        var violations = ModuleEdges.of(edges).stream()
                .filter(e -> !e.allowed())
                .map(e -> e.from() + " -> " + e.to() + " is not allowed by ArchModule."
                        + DependencyScanner.moduleOf(e.exampleFrom()).name() + ":\n  "
                        + e.exampleFrom() + " -> " + e.exampleTo())
                .toList();
        assertTrue(violations.isEmpty(), () -> String.join("\n", violations));
    }

    @Test
    void everyModuleHasClasses() {
        var used = edges.stream()
                .flatMap(e -> Stream.of(e.from(), e.to()))
                .map(DependencyScanner::moduleOf)
                .collect(Collectors.toSet());
        for (var module : ArchModule.values()) {
            assertTrue(used.contains(module), () -> module + " has no classes with dependencies");
        }
    }

}
