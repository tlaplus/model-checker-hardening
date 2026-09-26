package io.github.tlaplus.hardening.gen.library;

import at.forsyte.apalache.tla.lir.*;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.ir.IrNames;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypes;

/** Immutable prepared library. Mutable Apalache declarations never escape without a fresh copy. */
public final class OperatorLibrary {
    public record Export(OperatorId id, String name, OperT1 signature,
            Set<ExpressionCategory> categories, ModuleLink link) {
        public Export {
            categories = Set.copyOf(categories);
            Objects.requireNonNull(link, "link");
        }

        /** Whether the whole definition closure avoids every excluded category. */
        public boolean isEnabledWith(Set<ExpressionCategory> ignoredCategories) {
            return Collections.disjoint(categories, ignoredCategories);
        }
    }

    private record Definition(TlaOperDecl declaration, Set<String> dependencies,
            Set<ExpressionCategory> categories) {}

    private static final OperatorLibrary EMPTY = new OperatorLibrary(List.of(), Map.of());
    private final List<Export> exports;
    private final Map<OperatorId, Export> byId;
    private final Map<String, Definition> definitions;

    private OperatorLibrary(List<Export> exports, Map<String, Definition> definitions) {
        this.exports = List.copyOf(exports);
        var index = new LinkedHashMap<OperatorId, Export>();
        for (var export : exports) {
            if (index.put(export.id(), export) != null) {
                throw new IllegalArgumentException("duplicate custom operator: " + export.id());
            }
        }
        byId = Map.copyOf(index);
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public static OperatorLibrary empty() { return EMPTY; }
    public List<Export> exports() { return exports; }
    public Export get(OperatorId id) { return byId.get(id); }

    /** Links every module inline; see {@link #fromModules(Map, List, Map)}. */
    public static OperatorLibrary fromModules(Map<String, TlaModule> modules, List<OperatorId> selected) {
        return fromModules(modules, selected, Map.of());
    }

    /**
     * Retains only selected operators and their dependency closure. Selection order is decoder
     * order. A module absent from {@code links} is linked inline.
     */
    public static OperatorLibrary fromModules(Map<String, TlaModule> modules, List<OperatorId> selected,
            Map<String, ModuleLink> links) {
        var definitions = new LinkedHashMap<String, Definition>();
        var exports = new ArrayList<Export>();
        var indexes = new HashMap<String, Map<String, TlaOperDecl>>();
        for (var id : selected) {
            var module = modules.get(id.module());
            if (module == null) throw new IllegalArgumentException("missing custom module: " + id.module());
            var index = indexes.computeIfAbsent(id.module(), ignored -> index(module));
            if (!index.containsKey(id.operator())) {
                throw new IllegalArgumentException("selected custom operator not found: " + id);
            }
            var rename = names(id.module());
            collect(id.operator(), index, rename, definitions, new HashSet<>());
            var definition = definitions.get(rename.apply(id.operator()));
            var signature = signature(definition.declaration());
            var categories = EnumSet.noneOf(ExpressionCategory.class);
            closure(definition.declaration().name(), definitions, new LinkedHashSet<>())
                    .forEach(name -> categories.addAll(definitions.get(name).categories()));
            exports.add(new Export(id, definition.declaration().name(), signature, categories,
                    links.getOrDefault(id.module(), ModuleLink.of(id.module(), LibraryLinkage.INLINE))
                            .requireFor(id.module())));
        }
        return new OperatorLibrary(exports, definitions);
    }

    private static Map<String, TlaOperDecl> index(TlaModule module) {
        var result = new LinkedHashMap<String, TlaOperDecl>();
        for (var declaration : TlaModules.declarations(module)) {
            if (declaration instanceof TlaAssumeDecl) {
                throw new IllegalArgumentException("ASSUME is not supported in custom module " + module.name());
            }
            if (declaration instanceof TlaOperDecl operator) {
                if (result.put(operator.name(), operator) != null) {
                    throw new IllegalArgumentException("duplicate declaration: " + operator.name());
                }
            }
        }
        return result;
    }

    private static void collect(String name, Map<String, TlaOperDecl> index, UnaryOperator<String> rename,
            Map<String, Definition> definitions, Set<String> visiting) {
        var target = rename.apply(name);
        if (definitions.containsKey(target)) return;
        if (!visiting.add(name)) throw new IllegalArgumentException("recursive custom dependency: " + name);
        var declaration = index.get(name);
        if (declaration == null) {
            throw new IllegalArgumentException("missing operator or free constant/state variable: " + name);
        }
        signature(declaration);
        var facts = LibraryExpressions.inspect(declaration);
        for (var dependency : facts.freeNames().stream().sorted().toList()) {
            collect(dependency, index, rename, definitions, visiting);
        }
        var dependencies = new LinkedHashSet<String>();
        facts.freeNames().stream().sorted().map(rename).forEach(dependencies::add);
        definitions.put(target, new Definition(IrNames.rename(declaration, rename),
                Set.copyOf(dependencies), facts.categories()));
        visiting.remove(name);
    }

    private static OperT1 signature(TlaOperDecl declaration) {
        if (!(TlaTypes.typeOf(declaration) instanceof OperT1 signature)
                || TlaDeclarations.parameters(declaration).stream()
                        .anyMatch(parameter -> parameter.type() instanceof OperT1)) {
            throw new IllegalArgumentException("expected a first-order signature for " + declaration.name());
        }
        LibraryTypes.children(signature).forEach(OperatorLibrary::requireValueType);
        return signature;
    }

    private static void requireValueType(TlaType1 type) {
        if (type instanceof OperT1) {
            throw new IllegalArgumentException("higher-order custom signatures are not supported: " + type);
        }
        LibraryTypes.children(type).forEach(OperatorLibrary::requireValueType);
    }

    /** The definitions reachable from {@code name}, including it; unknown names are not library. */
    private static Set<String> closure(String name, Map<String, Definition> definitions, Set<String> reached) {
        var definition = definitions.get(name);
        if (definition == null || !reached.add(name)) return reached;
        definition.dependencies().forEach(dependency -> closure(dependency, definitions, reached));
        return reached;
    }

    /** The named instance through which the TLA+ source calls an aliased module's exports. */
    public static String instanceName(String module) {
        return "Custom" + hex(module) + "I";
    }

    /**
     * The name under which generated code applies the selected operator {@code id}. Known-defect
     * signatures name Community Modules operators by it, so it is part of the signature format.
     */
    public static String exportName(OperatorId id) {
        return names(id.module()).apply(id.operator());
    }

    private static UnaryOperator<String> names(String module) {
        var prefix = "Custom" + hex(module) + "N";
        return name -> prefix + hex(name);
    }

    private static String hex(String text) {
        return HexFormat.of().formatHex(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns fresh declarations for the used closure, in stable dependency order. */
    public List<TlaOperDecl> declarationsFor(List<TlaEx> expressions) {
        return declarationsFor(expressions, Set.of());
    }

    /** Fresh declarations for the used closure, omitting the closures of {@code aliased} exports. */
    private List<TlaOperDecl> declarationsFor(List<TlaEx> expressions, Set<String> aliased) {
        // Library names live in a namespace no generated binder uses, so every name reference
        // that matches a definition is one.
        var needed = new LinkedHashSet<String>();
        usedNames(expressions).stream().filter(name -> !aliased.contains(name))
                .forEach(name -> closure(name, definitions, needed));
        return definitions.entrySet().stream().filter(entry -> needed.contains(entry.getKey()))
                .map(entry -> TlaDeclarations.deepCopy(entry.getValue().declaration()))
                .toList();
    }

    private static Set<String> usedNames(List<TlaEx> expressions) {
        var names = new LinkedHashSet<String>();
        expressions.forEach(expression -> TlaExpressions.forEach(expression, node -> {
            if (node instanceof NameEx name) names.add(name.name());
        }));
        return names;
    }

    /** A closed standalone expression, without copying library bodies into richness scoring. */
    public TlaEx close(TlaEx expression) {
        var declarations = declarationsFor(List.of(expression));
        return declarations.isEmpty() ? expression : TlaExpressions.letIn(expression, declarations);
    }

    /** The self-contained module Apalache evaluates: every used definition is inlined. */
    public TlaModule link(TlaModule module, List<TlaEx> generated) {
        return prepend(module, declarationsFor(generated));
    }

    /**
     * The module the parser and TLC evaluate. Inline exports are linked as by {@link #link}; each
     * used export whose linkage aliases the source becomes an alias that the renderer defines
     * through its module's named instance, so generated call sites are unchanged.
     */
    public SourceLink linkSource(TlaModule module, List<TlaEx> generated) {
        var used = usedNames(generated);
        var aliases = aliases(export -> used.contains(export.name()));
        var aliased = aliases.stream().map(InstanceAlias::name).collect(java.util.stream.Collectors.toSet());
        return new SourceLink(prepend(module, declarationsFor(generated, aliased)), aliases);
    }

    /**
     * The aliases of every export whose linkage aliases the source, used or not, so preparation
     * can check once that the parser resolves them all.
     */
    public List<InstanceAlias> sourceAliases() {
        return aliases(export -> true);
    }

    private List<InstanceAlias> aliases(Predicate<Export> filter) {
        return exports.stream()
                .filter(export -> export.link().linkage().aliasesSource() && filter.test(export))
                .map(export -> new InstanceAlias(export.name(), export.id(), export.link().sourceModule(),
                        TlaTypes.operatorArguments(export.signature()).size()))
                .toList();
    }

    private static TlaModule prepend(TlaModule module, List<TlaOperDecl> library) {
        if (library.isEmpty()) return module;
        var declarations = new ArrayList<TlaDecl>(library);
        declarations.addAll(TlaModules.declarations(module));
        return TlaModules.create(module.name(), declarations);
    }
}
