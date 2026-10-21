package io.github.tlaplus.hardening.architecture.render;

import io.github.tlaplus.hardening.architecture.model.ArchModel.ModuleNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel.PackageNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel.Relation;
import io.github.tlaplus.hardening.architecture.model.ArchModel.TypeNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel;
import io.github.tlaplus.hardening.architecture.model.RelationKind;
import io.github.tlaplus.hardening.architecture.model.RoleLabels;
import io.github.tlaplus.hardening.architecture.model.TransitiveReduction.Arc;
import io.github.tlaplus.hardening.architecture.model.TransitiveReduction;
import io.github.tlaplus.hardening.common.Role;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Markdown pages of the atlas, keyed by their path relative to the atlas root: the module
 * graph in {@code README.md}, one page per module, and one page per type.
 */
final class MarkdownAtlas {
    /** The most relations a type page draws; its table lists them all. */
    static final int MAX_DRAWN_RELATIONS = 30;

    private final ModelIndex index;
    private final AtlasLinks links;

    MarkdownAtlas(ArchModel model) {
        this.index = new ModelIndex(model);
        this.links = new AtlasLinks(model.source());
    }

    Map<String, String> pages() {
        var pages = new LinkedHashMap<String, String>();
        pages.put("README.md", overview());
        for (var module : index.model().modules()) {
            pages.put(AtlasLinks.modulePage(module.name()), module(module));
        }
        for (var type : index.model().types()) {
            pages.put(AtlasLinks.typePage(type), type(type));
        }
        return pages;
    }

    private String overview() {
        var model = index.model();
        var out = new StringBuilder("# FuzzTLA architecture atlas\n\n");
        out.append("Generated from commit [`").append(links.shortCommit()).append("`](").append(links.commit())
                .append(") by `make architecture` (")
                .append(decisionLink("0022", "ADR 0022")).append("). The [interactive explorer](")
                .append(links.explorer()).append(") draws the same model.\n\n");
        out.append("## Modules\n\n")
                .append("An arrow points from a module to a module it depends on. An arrow that a longer ")
                .append("path implies is not drawn; the table lists every dependency. ")
                .append("A red arrow is a dependency that `ArchModule` does not allow.\n\n")
                .append(MermaidDiagrams.modules(model.modules(), model.moduleEdges())).append('\n');
        out.append("| Module | Summary | Types | Key types | Depends on |\n|---|---|---|---|---|\n");
        for (var module : model.modules()) {
            var types = index.typesOf(module);
            var dependsOn = model.moduleEdges().stream()
                    .filter(e -> e.from().equals(module.name()))
                    .map(e -> e.allowed() ? e.to() : "**" + e.to() + " (not allowed)**")
                    .collect(Collectors.joining(", "));
            out.append("| [").append(module.name()).append("](").append(AtlasLinks.modulePage(module.name()))
                    .append(") | ").append(cell(module.summary())).append(" | ").append(types.size())
                    .append(" | ").append(types.stream().filter(TypeNode::key).count())
                    .append(" | ").append(dependsOn).append(" |\n");
        }
        out.append("\n## Roles\n\n| Role | Meaning | Types |\n|---|---|---|\n");
        for (var role : Role.values()) {
            var count = model.types().stream().filter(t -> t.role() == role).count();
            out.append("| ").append(RoleLabels.of(role)).append(" | ").append(role.description())
                    .append(" | ").append(count).append(" |\n");
        }
        var untagged = model.types().stream().filter(t -> t.role() == null).count();
        out.append("| ").append(RoleLabels.UNTAGGED).append(" | no `@ArchRole` | ").append(untagged).append(" |\n");
        out.append("\n## Relations\n\n| Relation | Meaning |\n|---|---|\n");
        for (var kind : RelationKind.values()) {
            out.append("| ").append(kind.label()).append(" | ").append(kind.description()).append(" |\n");
        }
        return out.toString();
    }

    private String module(ModuleNode module) {
        var out = new StringBuilder("# Module `").append(module.name()).append("`\n\n");
        out.append("`").append(module.packageName()).append("` · [atlas](../README.md)\n\n");
        if (!module.summary().isEmpty()) {
            out.append(text(module.summary())).append("\n\n");
        }
        var edges = index.model().moduleEdges();
        out.append("**Depends on:** ").append(list(edges.stream()
                .filter(e -> e.from().equals(module.name())).map(e -> moduleLink(e.to())))).append("  \n");
        out.append("**Used by:** ").append(list(edges.stream()
                .filter(e -> e.to().equals(module.name())).map(e -> moduleLink(e.from())))).append("\n\n");

        var packages = index.packagesOf(module);
        if (packages.size() > 1) {
            out.append("## Packages\n\n")
                    .append("An arrow counts the relations other than `depends` from one package to another. ")
                    .append("An arrow that a longer path implies is not drawn.\n\n")
                    .append(MermaidDiagrams.packages(packages.stream().map(PackageNode::name).toList(),
                            packageArcs(module)))
                    .append('\n');
        }
        for (var pkg : packages) {
            out.append("## `").append(pkg.name()).append("`\n\n");
            if (!pkg.summary().isEmpty() && !pkg.name().equals(module.packageName())) {
                out.append(text(pkg.summary())).append("\n\n");
            }
            var types = index.typesOf(pkg);
            var keys = types.stream().filter(TypeNode::key).toList();
            if (!keys.isEmpty()) {
                var names = keys.stream().map(TypeNode::name).collect(Collectors.toSet());
                var among = structural().filter(r -> names.contains(r.from()) && names.contains(r.to())).toList();
                out.append(MermaidDiagrams.classes(keys, among, false)).append('\n');
            }
            out.append("| Type | Kind | Role | Summary |\n|---|---|---|---|\n");
            for (var type : types) {
                var name = type.key() ? "**" + type.simpleName() + "** ★" : type.simpleName();
                out.append("| [").append(name).append("](../").append(AtlasLinks.typePage(type)).append(") | ")
                        .append(kind(type)).append(" | ").append(RoleLabels.of(type.role())).append(" | ")
                        .append(cell(type.summary())).append(" |\n");
            }
            out.append('\n');
        }
        var adrs = index.adrsOf(module);
        if (!adrs.isEmpty()) {
            out.append("## Decisions\n\n");
            adrs.forEach(n -> out.append("- ").append(decisionLink(n, null)).append('\n'));
        }
        return out.toString();
    }

    /** The relations a diagram draws by default: every kind but {@link RelationKind#DEPENDS}. */
    private Stream<Relation> structural() {
        return index.model().relations().stream().filter(r -> r.kind() != RelationKind.DEPENDS);
    }

    /** The arcs between a module's packages that no longer path implies, with their relation counts. */
    private Map<Arc<String>, Integer> packageArcs(ModuleNode module) {
        var counts = new TreeMap<Arc<String>, Integer>(
                Comparator.comparing((Arc<String> a) -> a.from()).thenComparing(Arc::to));
        structural().forEach(r -> {
            var from = index.type(r.from());
            var to = index.type(r.to());
            if (index.moduleOf(from).equals(module.name()) && index.moduleOf(to).equals(module.name())
                    && !from.packageName().equals(to.packageName())) {
                counts.merge(new Arc<>(from.packageName(), to.packageName()), 1, Integer::sum);
            }
        });
        TransitiveReduction.implied(counts.keySet()).forEach(counts::remove);
        return counts;
    }

    private String type(TypeNode type) {
        var module = index.moduleOf(type);
        var out = new StringBuilder("# `").append(type.simpleName()).append("`\n\n");
        out.append("`").append(type.packageName()).append("` · ").append(kind(type)).append(" · ")
                .append(RoleLabels.of(type.role())).append(type.key() ? " · key" : "")
                .append(" · [source](").append(links.source(type)).append(") · module [")
                .append(module).append("](../").append(AtlasLinks.modulePage(module)).append(")\n\n");
        if (!type.summary().isEmpty()) {
            out.append(text(type.summary())).append("\n\n");
        }
        if (!type.adrs().isEmpty()) {
            out.append("**Decisions:** ").append(list(type.adrs().stream().map(n -> decisionLink(n, null))))
                    .append("\n\n");
        }
        var relations = new ArrayList<Relation>();
        relations.addAll(index.outgoing(type));
        relations.addAll(index.incoming(type));
        relations.sort(Comparator.comparing(Relation::kind));
        if (relations.isEmpty()) {
            out.append("No relations to other FuzzTLA types.\n");
            return out.toString();
        }
        var drawn = relations.stream().filter(r -> r.kind() != RelationKind.DEPENDS)
                .limit(MAX_DRAWN_RELATIONS).toList();
        if (!drawn.isEmpty()) {
            var shown = Stream.concat(Stream.of(type), drawn.stream()
                    .flatMap(r -> Stream.of(r.from(), r.to())).distinct()
                    .filter(n -> !n.equals(type.name())).map(index::type)).toList();
            out.append(MermaidDiagrams.classes(shown, drawn, false)).append('\n');
            if (drawn.size() < relations.size()) {
                out.append("The diagram draws ").append(drawn.size()).append(" of ").append(relations.size())
                        .append(" relations; the table lists them all.\n\n");
            }
        }
        out.append("| Direction | Relation | Type | Summary |\n|---|---|---|---|\n");
        for (var relation : relations) {
            var outgoing = relation.from().equals(type.name());
            var other = index.type(outgoing ? relation.to() : relation.from());
            out.append("| ").append(outgoing ? "→" : "←").append(" | ").append(relation.kind().label())
                    .append(" | [").append(other.simpleName()).append("](").append(other.name()).append(".md) | ")
                    .append(cell(other.summary())).append(" |\n");
        }
        return out.toString();
    }

    private String decisionLink(String number, String label) {
        return index.decision(number)
                .map(d -> "[" + (label != null ? label : "ADR " + number + ": " + d.title()) + "]("
                        + links.decision(d) + ")")
                .orElse(label != null ? label : "ADR " + number);
    }

    private static String moduleLink(String module) {
        return "[" + module + "](" + module + ".md)";
    }

    private static String kind(TypeNode type) {
        return type.kind().name().toLowerCase();
    }

    private static String list(Stream<String> items) {
        var all = items.toList();
        return all.isEmpty() ? "none" : String.join(", ", all);
    }

    /** Summary text for a Markdown paragraph: HTML-significant characters escaped. */
    private static String text(String summary) {
        return summary.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Summary text for a table cell: also escapes the column separator. */
    private static String cell(String summary) {
        return text(summary).replace("|", "\\|");
    }

}
