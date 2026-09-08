package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.OperT1;
import at.forsyte.apalache.tla.types.Substitution;
import at.forsyte.apalache.tla.types.TypeUnifier;
import at.forsyte.apalache.tla.types.TypeVarPool;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.library.OperatorId;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** One configured operator kind; its type scheme comes from the immutable prepared library. */
public record CustomExpressionKind(OperatorId id) implements ExpressionKind {
    public CustomExpressionKind { Objects.requireNonNull(id, "id"); }
    @Override public String name() { return id.toString(); }
    @Override public String configName() { return name(); }
    @Override public ExpressionCategory category() { return ExpressionCategory.OPERATOR; }
    @Override public Set<ExpressionCategory> requiredCategories() { return Set.of(category()); }
    @Override public boolean isTypeApplicable(IrType type) { return !(type instanceof OperatorType); }

    @Override
    public boolean isConfiguredApplicable(IrGenerationConfig config, IrType type) {
        return isTypeApplicable(type) && plan(config, type).isPresent();
    }

    /** Rejects enabled signatures that cannot produce any supported value under these limits. */
    static void requireUsableLibrary(IrGenerationConfig config) {
        for (var export : config.library().exports()) {
            if (Collections.disjoint(export.categories(), config.ignoredCategories())
                    && TypeInstantiation.plan(export.signature(), config.expressions(),
                            config.ignoredCategories(), config.expressions().maximumTypeDepth()).isEmpty()) {
                throw new IllegalArgumentException("custom operator " + export.id()
                        + " has no supported instantiation within the configured type limits");
            }
        }
    }

    Optional<TypeInstantiation> plan(IrGenerationConfig config, IrType result) {
        var export = config.library().get(id);
        if (export == null || !Collections.disjoint(export.categories(), config.ignoredCategories())) {
            return Optional.empty();
        }
        var signature = (OperT1) TypeInstantiation.canonical(export.signature());
        var pool = new TypeVarPool(signature.usedNames().size());
        var unified = new TypeUnifier(pool).unify(Substitution.empty(), signature.res(), result.toTlaType());
        if (unified.isEmpty() || !unified.get()._2().equals(result.toTlaType())) return Optional.empty();
        var partial = unified.get()._1().subRec(signature);
        return TypeInstantiation.plan(partial, config.expressions(), config.ignoredCategories(),
                config.expressions().maximumTypeDepth());
    }
}
