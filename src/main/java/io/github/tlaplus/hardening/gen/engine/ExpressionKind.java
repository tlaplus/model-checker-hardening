package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.ExpressionCategory;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** A selectable expression form grouped by the component that constructs it. */
public sealed interface ExpressionKind
        permits GeneralExpressionKind,
                BooleanExpressionKind,
                IntegerExpressionKind,
                SetExpressionKind,
                SequenceExpressionKind,
                OtherExpressionKind {
    /** Slots a form occupies when its weight is not configured. */
    int DEFAULT_WEIGHT = 1;

    /** Reports whether this form can produce the requested type, independent of lexical scope. */
    boolean isTypeApplicable(IrType type);

    /**
     * Returns how many selection slots this form occupies for the requested type, or zero when the
     * current lexical scope cannot supply what the form needs.
     */
    default int selectionWeight(GenerationContext context, IrType type) {
        return context.config().weightOf(this);
    }

    /** Returns the lowercase name used in {@code generator.weights}. */
    default String configName() {
        return ((Enum<?>) this).name().toLowerCase(Locale.ROOT);
    }

    /** Returns this form's single primary user-facing category. */
    ExpressionCategory category();

    /** Returns the syntax capabilities required to construct this form. */
    Set<ExpressionCategory> requiredCategories();

    /** Reports whether this form requires one of the supplied exclusion categories. */
    default boolean isUnavailableWith(Set<ExpressionCategory> ignoredCategories) {
        for (var category : requiredCategories()) {
            if (ignoredCategories.contains(category)) {
                return true;
            }
        }
        return false;
    }

    /** Builds an immutable requirement set containing the primary category. */
    static Set<ExpressionCategory> requirements(
            ExpressionCategory category, ExpressionCategory... dependencies) {
        var result = EnumSet.of(category);
        Collections.addAll(result, dependencies);
        return Set.copyOf(result);
    }
}
