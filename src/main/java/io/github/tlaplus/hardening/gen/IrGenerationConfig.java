package io.github.tlaplus.hardening.gen;

import io.github.tlaplus.hardening.gen.engine.ExpressionKind;
import io.github.tlaplus.hardening.gen.engine.GeneralExpressionKind;
import io.github.tlaplus.hardening.gen.engine.SetExpressionKind;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Settings for generated TLA+ expressions and modules.
 *
 * @param expressions bounds on recursive construction within one expression
 * @param modules bounds on the declarations of one generated module
 * @param ignoredCategories syntax capabilities excluded before byte-level selection
 * @param formWeights selection slots per expression form, for the forms that are not weighted one
 */
public record IrGenerationConfig(
        ExpressionLimits expressions,
        ModuleLimits modules,
        Set<ExpressionCategory> ignoredCategories,
        Map<ExpressionKind, Integer> formWeights) {

    /**
     * Largest slot count a single form may occupy. The bound is arbitrary but deliberate: a form
     * weighted beyond this crowds out the rest of the catalog rather than biasing towards it.
     */
    public static final int MAXIMUM_FORM_WEIGHT = 64;

    /**
     * Forms whose default weight is not one, chosen by sweeping each weight against how often
     * property-based inputs contain a membership test on a fold parameter against a non-empty set
     * literal. Uniform selection leaves a random expression rarely referring to its own bindings
     * and rarely building a non-empty collection literal, and a construct whose meaning lives in
     * those positions is then generated but never exercised.
     *
     * <p>The set literal dominates: raising its weight from 1 to 16 took that shape from 148 to
     * 1168 per 20000 admitted inputs, while the same sweep over the name weight barely moved it.
     * A weighted terminal measured slightly worse than none, because it crowds out the literals
     * that would otherwise be the other side of a comparison.
     */
    private static final Map<ExpressionKind, Integer> DEFAULT_FORM_WEIGHTS = Map.of(
            GeneralExpressionKind.NAME, 8,
            SetExpressionKind.ENUM_SET, 16);

    public IrGenerationConfig {
        Objects.requireNonNull(expressions, "expressions");
        Objects.requireNonNull(modules, "modules");
        ignoredCategories = Set.copyOf(
                Objects.requireNonNull(ignoredCategories, "ignoredCategories"));
        if (ignoredCategories.stream().anyMatch(category -> !category.isIgnorable())) {
            throw new IllegalArgumentException("the core expression category cannot be ignored");
        }
        Objects.requireNonNull(formWeights, "formWeights");
        for (var entry : formWeights.entrySet()) {
            Objects.requireNonNull(entry.getKey(), "form");
            var weight = entry.getValue();
            if (weight == null
                    || weight < ExpressionKind.DEFAULT_WEIGHT
                    || weight > MAXIMUM_FORM_WEIGHT) {
                throw new IllegalArgumentException(
                        "weight of '" + entry.getKey().configName() + "' must be in the range "
                                + ExpressionKind.DEFAULT_WEIGHT + ".." + MAXIMUM_FORM_WEIGHT);
            }
        }
        formWeights = copyOf(formWeights);
    }

    /** Returns an unmodifiable snapshot that iterates in declaration order. */
    private static Map<ExpressionKind, Integer> copyOf(Map<ExpressionKind, Integer> weights) {
        var copy = new LinkedHashMap<ExpressionKind, Integer>();
        for (var kind : ExpressionKind.all()) {
            if (weights.containsKey(kind)) {
                copy.put(kind, weights.get(kind));
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    /** Returns the selection slots {@code kind} occupies, defaulting to one. */
    public int weightOf(ExpressionKind kind) {
        Objects.requireNonNull(kind, "kind");
        return formWeights.getOrDefault(kind, ExpressionKind.DEFAULT_WEIGHT);
    }

    /** Returns the slots the configured weights add beyond one per form. */
    public int additionalSelectionSlots() {
        var additional = 0;
        for (var weight : formWeights.values()) {
            additional += weight - ExpressionKind.DEFAULT_WEIGHT;
        }
        return additional;
    }

    /**
     * Returns these settings with further categories excluded.
     *
     * <p>Module generation uses this to take the action and temporal forms out of every
     * subexpression it draws: it constructs all priming and {@code UNCHANGED} itself, and a prime
     * appearing under a negation or a quantifier would break the assignment completeness that
     * makes the module admissible.
     */
    public IrGenerationConfig ignoring(ExpressionCategory... categories) {
        Objects.requireNonNull(categories, "categories");
        var ignored = EnumSet.noneOf(ExpressionCategory.class);
        ignored.addAll(ignoredCategories);
        Collections.addAll(ignored, categories);
        return new IrGenerationConfig(expressions, modules, ignored, formWeights);
    }

    /** Returns these settings with a different set of excluded categories. */
    public IrGenerationConfig withIgnoredCategories(Set<ExpressionCategory> categories) {
        return new IrGenerationConfig(expressions, modules, categories, formWeights);
    }

    /** Returns these settings with different expression limits. */
    public IrGenerationConfig withExpressionLimits(ExpressionLimits limits) {
        return new IrGenerationConfig(limits, modules, ignoredCategories, formWeights);
    }

    /** Returns these settings with different module limits. */
    public IrGenerationConfig withModuleLimits(ModuleLimits limits) {
        return new IrGenerationConfig(expressions, limits, ignoredCategories, formWeights);
    }

    /** Returns these settings with different form weights. */
    public IrGenerationConfig withFormWeights(Map<ExpressionKind, Integer> weights) {
        return new IrGenerationConfig(expressions, modules, ignoredCategories, weights);
    }

    public static IrGenerationConfig defaults() {
        return new IrGenerationConfig(
                ExpressionLimits.defaults(),
                ModuleLimits.defaults(),
                Set.of(
                        ExpressionCategory.ACTION,
                        ExpressionCategory.TEMPORAL,
                        ExpressionCategory.UNBOUND,
                        ExpressionCategory.EXOTIC),
                DEFAULT_FORM_WEIGHTS);
    }
}
