package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.OperT1;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.library.OperatorId;
import java.util.Objects;
import java.util.Optional;
import org.apalache_mc.tla.jir.TlaTypeUnifier;

/** One configured operator kind; its type scheme comes from the immutable prepared library. */
public record CustomExpressionKind(OperatorId id) implements ExpressionKind {
    private static final Categories CATEGORIES = new Categories(ExpressionCategory.OPERATOR);

    public CustomExpressionKind { Objects.requireNonNull(id, "id"); }
    @Override public String name() { return id.toString(); }
    @Override public String configName() { return name(); }
    @Override public Categories categories() { return CATEGORIES; }
    @Override public boolean isTypeApplicable(IrType type) { return !(type instanceof OperatorType); }

    @Override
    public boolean isConfiguredApplicable(IrGenerationConfig config, IrType type) {
        return isTypeApplicable(type) && plan(config, type).isPresent();
    }

    /** Rejects enabled signatures that cannot produce any supported value under these limits. */
    static void requireUsableLibrary(IrGenerationConfig config) {
        for (var export : config.library().exports()) {
            if (export.isEnabledWith(config.ignoredCategories())
                    && TypeInstantiation.plan(export.signature(), config).isEmpty()) {
                throw new IllegalArgumentException("custom operator " + export.id()
                        + " has no supported instantiation within the configured type limits");
            }
        }
    }

    /** Matches this operator's result against the requested type, then plans the residual variables. */
    Optional<TypeInstantiation> plan(IrGenerationConfig config, IrType result) {
        var export = config.library().get(id);
        if (export == null || !export.isEnabledWith(config.ignoredCategories())) return Optional.empty();
        var signature = (OperT1) TypeInstantiation.canonical(export.signature());
        var unified = new TlaTypeUnifier(signature)
                .unify(Optional.empty(), signature.res(), result.toTlaType());
        if (unified.isEmpty() || !unified.orElseThrow().unifiedType().equals(result.toTlaType())) {
            return Optional.empty();
        }
        return TypeInstantiation.plan(
                unified.orElseThrow().substitution().applyFully(signature), config);
    }
}
