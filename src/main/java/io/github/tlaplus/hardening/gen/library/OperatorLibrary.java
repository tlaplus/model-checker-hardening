package io.github.tlaplus.hardening.gen.library;

import at.forsyte.apalache.tla.lir.*;
import io.github.tlaplus.hardening.common.TlaExpressions;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.UnaryOperator;
import static io.github.tlaplus.hardening.common.ScalaCollections.*;

/** Immutable prepared library. Mutable Apalache declarations never escape without a fresh copy. */
public final class OperatorLibrary {
    public record Export(OperatorId id, String name, OperT1 signature,
            Set<ExpressionCategory> categories) {
        public Export { categories = Set.copyOf(categories); }

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

    /** Retains only selected operators and their dependency closure. Selection order is decoder order. */
    public static OperatorLibrary fromModules(Map<String, TlaModule> modules, List<OperatorId> selected) {
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
            exports.add(new Export(id, definition.declaration().name(), signature, categories));
        }
        return new OperatorLibrary(exports, definitions);
    }

    private static Map<String, TlaOperDecl> index(TlaModule module) {
        var result = new LinkedHashMap<String, TlaOperDecl>();
        for (var declaration : list(module.declarations())) {
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
        definitions.put(target, new Definition(LibraryExpressions.rename(declaration, rename),
                Set.copyOf(dependencies), facts.categories()));
        visiting.remove(name);
    }

    private static OperT1 signature(TlaOperDecl declaration) {
        if (!(LibraryTypes.type(declaration.typeTag()) instanceof OperT1 signature)
                || signature.args().size() != declaration.formalParams().size()
                || list(declaration.formalParams()).stream().anyMatch(p -> p.arity() != 0)) {
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

    private static UnaryOperator<String> names(String module) {
        var prefix = "Custom" + hex(module) + "N";
        return name -> prefix + hex(name);
    }

    private static String hex(String text) {
        return HexFormat.of().formatHex(text.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns fresh declarations for the used closure, in stable dependency order. */
    public List<TlaOperDecl> declarationsFor(List<TlaEx> expressions) {
        // Library names live in a namespace no generated binder uses, so every name reference
        // that matches a definition is one.
        var needed = new LinkedHashSet<String>();
        expressions.forEach(expression -> TlaExpressions.forEach(expression, node -> {
            if (node instanceof NameEx name) closure(name.name(), definitions, needed);
        }));
        return definitions.entrySet().stream().filter(entry -> needed.contains(entry.getKey()))
                .map(entry -> TlaExpressions.copy(entry.getValue().declaration()))
                .toList();
    }

    /** A closed standalone expression, without copying library bodies into richness scoring. */
    public TlaEx close(TlaEx expression) {
        var declarations = declarationsFor(List.of(expression));
        return declarations.isEmpty() ? expression
                : new LetInEx(expression, seq(declarations), expression.typeTag());
    }

    public TlaModule link(TlaModule module, List<TlaEx> generated) {
        var declarations = new ArrayList<TlaDecl>(declarationsFor(generated));
        if (declarations.isEmpty()) return module;
        declarations.addAll(list(module.declarations()));
        return new TlaModule(module.name(), seq(declarations));
    }
}
