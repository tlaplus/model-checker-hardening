package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.ModuleLimits;
import java.util.EnumSet;
import java.util.Objects;

/**
 * What a generic checker supports of the reference checker's conventions (ADR 0033 §7, §8). A
 * missing capability shapes the whole corpus: every checker gets the inputs the tool can read.
 *
 * @param errors how much of the exit status the aggregator compares
 * @param fairness whether the tool checks {@code WF_v(A)} and {@code SF_v(A)}; without it the
 *     corpus generates no fairness, neither as conditions of the specification nor in properties
 * @param apalache whether the tool reads the {@code Apalache} and {@code Variants} modules; without
 *     it the corpus generates none of their operators, the folds and variants, and the tool gets
 *     its module without them in {@code EXTENDS}
 */
@ArchRole(Role.CONFIG)
public record GenericCapabilities(GenericErrors errors, boolean fairness, boolean apalache) {
    /** Exact exit statuses, fairness and the Apalache modules: what TLC itself supports. */
    public static final GenericCapabilities FULL = new GenericCapabilities(GenericErrors.EXACT, true, true);

    public GenericCapabilities {
        Objects.requireNonNull(errors, "errors");
    }

    /** Returns {@code generator} restricted to the forms this tool supports. */
    IrGenerationConfig restrict(IrGenerationConfig generator) {
        var ignored = EnumSet.noneOf(ExpressionCategory.class);
        ignored.addAll(generator.ignoredCategories());
        var modules = generator.modules();
        if (!fairness) {
            ignored.add(ExpressionCategory.FAIRNESS);
            modules = modules.withMaximumFairnessConditions(0);
        }
        if (!apalache) {
            ignored.add(ExpressionCategory.FOLD);
            ignored.add(ExpressionCategory.VARIANT);
        }
        return generator.withIgnoredCategories(ignored).withModuleLimits(modules);
    }
}
