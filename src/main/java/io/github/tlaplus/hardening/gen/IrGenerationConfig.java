package io.github.tlaplus.hardening.gen;

import io.github.tlaplus.hardening.gen.engine.ExpressionKind;
import io.github.tlaplus.hardening.gen.engine.ExpressionKinds;
import io.github.tlaplus.hardening.gen.engine.GeneralExpressionKind;
import io.github.tlaplus.hardening.gen.engine.SetExpressionKind;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Settings and resource limits for generated TLA+ expressions. */
public record IrGenerationConfig(
        int maximumTypeDepth,
        int maximumExpressionDepth,
        int maximumNodes,
        int maximumCollectionSize,
        int maximumStringBytes,
        int maximumIntegerBytes,
        Set<ExpressionCategory> ignoredCategories,
        Map<ExpressionKind, Integer> formWeights) {

    public static final int DEFAULT_MAXIMUM_TYPE_DEPTH = 3;
    public static final int DEFAULT_MAXIMUM_EXPRESSION_DEPTH = 32;
    public static final int DEFAULT_MAXIMUM_NODES = 128;
    public static final int DEFAULT_MAXIMUM_COLLECTION_SIZE = 8;
    public static final int DEFAULT_MAXIMUM_STRING_BYTES = 32;
    public static final int DEFAULT_MAXIMUM_INTEGER_BYTES = 16;

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
        if (maximumTypeDepth < 0) {
            throw new IllegalArgumentException("maximumTypeDepth must be nonnegative");
        }
        if (maximumExpressionDepth < 1) {
            throw new IllegalArgumentException("maximumExpressionDepth must be positive");
        }
        if (maximumNodes < 1) {
            throw new IllegalArgumentException("maximumNodes must be positive");
        }
        if (maximumCollectionSize < 1) {
            throw new IllegalArgumentException("maximumCollectionSize must be positive");
        }
        if (maximumStringBytes < 0) {
            throw new IllegalArgumentException("maximumStringBytes must be nonnegative");
        }
        if (maximumIntegerBytes < 0) {
            throw new IllegalArgumentException("maximumIntegerBytes must be nonnegative");
        }
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
        for (var kind : ExpressionKinds.all()) {
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

    public static IrGenerationConfig defaults() {
        return new IrGenerationConfig(
                DEFAULT_MAXIMUM_TYPE_DEPTH,
                DEFAULT_MAXIMUM_EXPRESSION_DEPTH,
                DEFAULT_MAXIMUM_NODES,
                DEFAULT_MAXIMUM_COLLECTION_SIZE,
                DEFAULT_MAXIMUM_STRING_BYTES,
                DEFAULT_MAXIMUM_INTEGER_BYTES,
                Set.of(
                        ExpressionCategory.ACTION,
                        ExpressionCategory.TEMPORAL,
                        ExpressionCategory.UNBOUND,
                        ExpressionCategory.EXOTIC),
                DEFAULT_FORM_WEIGHTS);
    }
}
