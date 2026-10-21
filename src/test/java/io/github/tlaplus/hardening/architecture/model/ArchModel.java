package io.github.tlaplus.hardening.architecture.model;

import io.github.tlaplus.hardening.common.Role;
import java.util.List;

/**
 * What the architecture atlas knows about the main sources, and what both of its views draw
 * (ADR 0022). Every list is sorted, so equal sources give equal models.
 */
public record ArchModel(
        Source source,
        List<Decision> decisions,
        List<ModuleNode> modules,
        List<PackageNode> packages,
        List<TypeNode> types,
        List<Relation> relations,
        List<ModuleEdge> moduleEdges) {

    /** The repository and commit that source links point to. */
    public record Source(String repository, String commit) {}

    /** One architecture decision record, which summaries refer to as "ADR NNNN". */
    public record Decision(String number, String title, String path) {}

    /** A top-level package, as {@code ArchModule} declares it. */
    public record ModuleNode(
            String name, String packageName, String summary, List<String> allowed) {}

    /** A package, with the module it belongs to and the summary of its {@code package-info}. */
    public record PackageNode(String name, String module, String summary, List<String> adrs) {}

    /**
     * A top-level type. {@code role} is null for a type without {@code @ArchRole}; {@code path}
     * is relative to the repository root.
     */
    public record TypeNode(
            String name,
            String packageName,
            TypeKind kind,
            Role role,
            boolean key,
            String summary,
            String path,
            int line,
            List<String> adrs) {

        /** The name without its package. */
        public String simpleName() {
            return name.substring(name.lastIndexOf('.') + 1);
        }
    }

    /** The strongest relation from one top-level type to another. */
    public record Relation(String from, String to, RelationKind kind) {}

    /**
     * The class dependencies from one module to another: how many type pairs, whether {@code
     * ArchModule} allows them, whether a longer path of module edges implies the edge, and the
     * first pair in name order.
     */
    public record ModuleEdge(
            String from,
            String to,
            int typePairs,
            boolean allowed,
            boolean implied,
            String exampleFrom,
            String exampleTo) {}

    /** The kind of a top-level type. */
    public enum TypeKind {
        CLASS,
        INTERFACE,
        ENUM,
        RECORD,
        ANNOTATION
    }
}
