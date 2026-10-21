package io.github.tlaplus.hardening.architecture.extract;

import io.github.tlaplus.hardening.architecture.extract.DependencyScanner.ClassEdge;
import io.github.tlaplus.hardening.architecture.model.ArchModel.Decision;
import io.github.tlaplus.hardening.architecture.model.ArchModel.ModuleNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel.PackageNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel.Relation;
import io.github.tlaplus.hardening.architecture.model.ArchModel.Source;
import io.github.tlaplus.hardening.architecture.model.ArchModel.TypeNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel;
import io.github.tlaplus.hardening.architecture.model.RelationKind;
import io.github.tlaplus.hardening.common.ArchModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import jdk.javadoc.doclet.DocletEnvironment;

/** Assembles the atlas model from the doclet's elements, {@code jdeps}, and the ADR directory. */
final class ModelBuilder {
    private static final Pattern DECISION = Pattern.compile("(\\d{4})-.*\\.md");
    private static final Pattern TITLE = Pattern.compile("#\\s*\\d{4}:\\s*(.*)");

    private ModelBuilder() {}

    static ArchModel build(DocletEnvironment environment, AtlasSettings settings) throws IOException {
        var extractor = new ModelExtractor(environment.getDocTrees(), settings.root());
        var elements = ElementFilter.typesIn(environment.getIncludedElements()).stream()
                .filter(t -> t.getEnclosingElement() instanceof PackageElement)
                .sorted(Comparator.comparing(t -> t.getQualifiedName().toString()))
                .toList();
        var types = elements.stream().map(extractor::typeNode).toList();
        var known = types.stream().map(TypeNode::name).collect(Collectors.toSet());

        var packages = new TreeMap<String, PackageNode>();
        for (var type : elements) {
            var pkg = (PackageElement) type.getEnclosingElement();
            packages.computeIfAbsent(pkg.getQualifiedName().toString(), n -> extractor.packageNode(pkg));
        }

        var kinds = new TreeMap<ClassEdge, RelationKind>();
        for (var type : elements) {
            var from = type.getQualifiedName().toString();
            extractor.relations(type, known).forEach((to, kind) -> kinds.put(new ClassEdge(from, to), kind));
        }
        var edges = DependencyScanner.scan(settings.classes());
        for (var edge : edges) {
            if (known.contains(edge.from()) && known.contains(edge.to())) {
                kinds.putIfAbsent(edge, RelationKind.DEPENDS);
            }
        }
        var relations = kinds.entrySet().stream()
                .map(e -> new Relation(e.getKey().from(), e.getKey().to(), e.getValue()))
                .toList();

        return new ArchModel(
                new Source(settings.repository(), settings.commit()),
                decisions(settings),
                modules(packages),
                List.copyOf(packages.values()),
                types,
                relations,
                ModuleEdges.of(edges));
    }

    private static List<ModuleNode> modules(Map<String, PackageNode> packages) {
        return Arrays.stream(ArchModule.values())
                .map(m -> new ModuleNode(
                        m.displayName(),
                        m.packageName(),
                        packages.containsKey(m.packageName()) ? packages.get(m.packageName()).summary() : "",
                        m.allowed().stream().map(ArchModule::displayName).toList()))
                .toList();
    }

    private static List<Decision> decisions(AtlasSettings settings) throws IOException {
        var root = settings.root().toAbsolutePath().normalize();
        try (var files = Files.list(settings.decisions())) {
            var decisions = new ArrayList<Decision>();
            for (var file : files.sorted().toList()) {
                var matcher = DECISION.matcher(file.getFileName().toString());
                if (matcher.matches()) {
                    var relative = root.relativize(file.toAbsolutePath().normalize());
                    decisions.add(new Decision(matcher.group(1), title(file), slashed(relative)));
                }
            }
            return decisions;
        }
    }

    private static String title(Path file) throws IOException {
        try (var lines = Files.lines(file)) {
            return lines.findFirst()
                    .map(TITLE::matcher)
                    .filter(java.util.regex.Matcher::matches)
                    .map(m -> m.group(1).strip())
                    .orElse(file.getFileName().toString());
        }
    }

    private static String slashed(Path path) {
        return path.toString().replace(path.getFileSystem().getSeparator(), "/");
    }

}
