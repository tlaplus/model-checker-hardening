package io.github.tlaplus.hardening.architecture.render;

import io.github.tlaplus.hardening.architecture.model.ArchModel.Decision;
import io.github.tlaplus.hardening.architecture.model.ArchModel.ModuleNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel.PackageNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel.Relation;
import io.github.tlaplus.hardening.architecture.model.ArchModel.TypeNode;
import io.github.tlaplus.hardening.architecture.model.ArchModel;
import io.github.tlaplus.hardening.common.Role;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Lookups over one atlas model that the page renderers share. */
final class ModelIndex {
    private final ArchModel model;
    private final Map<String, TypeNode> types;
    private final Map<String, PackageNode> packages;
    private final Map<String, Decision> decisions;

    ModelIndex(ArchModel model) {
        this.model = model;
        this.types = byKey(model.types(), TypeNode::name);
        this.packages = byKey(model.packages(), PackageNode::name);
        this.decisions = byKey(model.decisions(), Decision::number);
    }

    ArchModel model() {
        return model;
    }

    TypeNode type(String name) {
        return types.get(name);
    }

    Optional<Decision> decision(String number) {
        return Optional.ofNullable(decisions.get(number));
    }

    String moduleOf(TypeNode type) {
        return packages.get(type.packageName()).module();
    }

    List<PackageNode> packagesOf(ModuleNode module) {
        return model.packages().stream().filter(p -> p.module().equals(module.name())).toList();
    }

    List<TypeNode> typesOf(PackageNode pkg) {
        return model.types().stream().filter(t -> t.packageName().equals(pkg.name())).toList();
    }

    List<TypeNode> typesOf(ModuleNode module) {
        return model.types().stream().filter(t -> moduleOf(t).equals(module.name())).toList();
    }

    List<Relation> outgoing(TypeNode type) {
        return model.relations().stream().filter(r -> r.from().equals(type.name())).toList();
    }

    List<Relation> incoming(TypeNode type) {
        return model.relations().stream().filter(r -> r.to().equals(type.name())).toList();
    }

    /** The numbers of the ADRs that a module's package and type summaries mention, in order. */
    List<String> adrsOf(ModuleNode module) {
        var fromPackages = packagesOf(module).stream().flatMap(p -> p.adrs().stream());
        var fromTypes = typesOf(module).stream().flatMap(t -> t.adrs().stream());
        return java.util.stream.Stream.concat(fromPackages, fromTypes).distinct().sorted().toList();
    }

    private static <T> Map<String, T> byKey(List<T> values, Function<T, String> key) {
        return values.stream().collect(Collectors.toMap(key, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }
}
