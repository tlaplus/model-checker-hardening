package io.github.tlaplus.hardening.architecture.render;

import io.github.tlaplus.hardening.architecture.model.ArchModel.ModuleEdge;
import io.github.tlaplus.hardening.architecture.model.ArchModel.ModuleNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel.Relation;
import io.github.tlaplus.hardening.architecture.model.ArchModel.TypeKind;
import io.github.tlaplus.hardening.architecture.model.ArchModel.TypeNode;
import io.github.tlaplus.hardening.architecture.model.RelationKind;
import io.github.tlaplus.hardening.architecture.model.RoleLabels;
import io.github.tlaplus.hardening.common.ArchModule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Mermaid sources of the atlas's diagrams, each as a fenced Markdown block. */
final class MermaidDiagrams {
    /** The module that every module may use; module views hide most of its edges. */
    static final String LEAF = ArchModule.COMMON.displayName();

    private MermaidDiagrams() {}

    /**
     * The module graph, top-down from the launcher, with disallowed edges in red. An edge that a
     * longer path implies is drawn only when it is disallowed.
     */
    static String modules(List<ModuleNode> modules, List<ModuleEdge> edges) {
        var out = new StringBuilder("```mermaid\nflowchart TB\n");
        for (var module : modules) {
            out.append("  ").append(module.name()).append("[\"").append(module.name()).append("\"]\n");
        }
        var red = new ArrayList<Integer>();
        var drawn = edges.stream().filter(e -> !e.allowed() || !e.implied()).toList();
        for (var i = 0; i < drawn.size(); i++) {
            var edge = drawn.get(i);
            out.append("  ").append(edge.from()).append(" --> ").append(edge.to()).append('\n');
            if (!edge.allowed()) {
                red.add(i);
            }
        }
        for (var index : red) {
            out.append("  linkStyle ").append(index).append(" stroke:#d1242f,stroke-width:3px\n");
        }
        return out.append("```\n").toString();
    }

    /**
     * A class diagram of {@code types} and the relations among them. With {@code byPackage}, each
     * package is a namespace.
     */
    static String classes(Collection<TypeNode> types, Collection<Relation> relations, boolean byPackage) {
        var ids = ids(types);
        var out = new StringBuilder("```mermaid\nclassDiagram\n");
        if (byPackage) {
            var packages = types.stream().collect(Collectors.groupingBy(
                    TypeNode::packageName, LinkedHashMap::new, Collectors.toList()));
            packages.forEach((pkg, members) -> {
                out.append("  namespace ").append(namespace(pkg)).append(" {\n");
                members.forEach(t -> declare(out, "    ", ids.get(t.name()), t));
                out.append("  }\n");
            });
        } else {
            types.forEach(t -> declare(out, "  ", ids.get(t.name()), t));
        }
        for (var relation : relations) {
            var from = ids.get(relation.from());
            var to = ids.get(relation.to());
            if (from != null && to != null) {
                out.append("  ").append(arrow(relation.kind(), from, to)).append('\n');
            }
        }
        return out.append("```\n").toString();
    }

    private static void declare(StringBuilder out, String indent, String id, TypeNode type) {
        out.append(indent).append("class ").append(id);
        var stereotype = stereotype(type);
        if (stereotype.isEmpty()) {
            out.append('\n');
        } else {
            out.append(" {\n").append(indent).append("  <<").append(stereotype).append(">>\n")
                    .append(indent).append("}\n");
        }
    }

    private static String stereotype(TypeNode type) {
        if (type.role() != null) {
            return RoleLabels.of(type.role());
        }
        return type.kind() == TypeKind.CLASS ? "" : type.kind().name().toLowerCase();
    }

    private static String arrow(RelationKind kind, String from, String to) {
        return switch (kind) {
            case EXTENDS -> to + " <|-- " + from;
            case IMPLEMENTS -> to + " <|.. " + from;
            case HAS -> from + " --> " + to + " : has";
            case USES -> from + " ..> " + to + " : uses";
            case DEPENDS -> from + " ..> " + to + " : depends";
        };
    }

    /** Simple names as diagram ids, qualified by package segments where simple names collide. */
    private static Map<String, String> ids(Collection<TypeNode> types) {
        var counts = types.stream().collect(Collectors.groupingBy(TypeNode::simpleName, Collectors.counting()));
        var ids = new HashMap<String, String>();
        for (var type : types) {
            var unique = counts.get(type.simpleName()) == 1;
            ids.put(type.name(), unique ? type.simpleName() : namespace(type.packageName()) + "_" + type.simpleName());
        }
        return ids;
    }

    private static String namespace(String packageName) {
        if (packageName.equals(ArchModule.ROOT_PACKAGE)) {
            return "root";
        }
        return packageName.substring(ArchModule.ROOT_PACKAGE.length() + 1).replace('.', '_');
    }

}
