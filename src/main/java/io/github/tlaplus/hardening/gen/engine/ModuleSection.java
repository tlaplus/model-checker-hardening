package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.ExpressionCategory;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * The independent parts of a module input, each decoded from its own contiguous range of bytes.
 *
 * <p>A module has several top-level bodies, and one cursor drawn through all of them in turn lets
 * whichever body comes first decide how many bytes the later ones see. Measured over a
 * property-based corpus, a median input of 218 bytes was spent on the invariant before the
 * next-state action was drawn, and two thirds of the modules had a {@code Next} that only
 * stuttered. Giving each body a fixed share of the input removes that dependence, and keeps a
 * byte mutation inside one body from reframing the others.
 *
 * <p>Declaration order is the byte layout and the draw order, and each constant's weight is its
 * share of the input. Both are part of the byte encoding: {@code ModuleSectionTest} pins them, so
 * reordering or reweighting a section fails a test instead of silently reinterpreting a corpus.
 */
enum ModuleSection {
    /** State-variable types. */
    VARIABLES(1),
    /** State-free definitions that every later body may apply. */
    AUXILIARY_OPERATORS(2),
    /** The state invariant. */
    INVARIANT(3),
    /** Definitions that read current state and prime their effect, applicable only in Next. */
    ACTION_OPERATORS(3),
    /** The initial-state predicate. */
    INIT(2),
    /** The next-state action. */
    NEXT(5),
    /** The temporal property and its fairness, drawn only with the temporal category enabled. */
    PROPERTY(3, ExpressionCategory.TEMPORAL);

    private final int weight;
    private final Optional<ExpressionCategory> requiredCategory;

    ModuleSection(int weight) {
        this.weight = weight;
        this.requiredCategory = Optional.empty();
    }

    ModuleSection(int weight, ExpressionCategory requiredCategory) {
        this.weight = weight;
        this.requiredCategory = Optional.of(requiredCategory);
    }

    /** Returns this section's share of the input, relative to the sum over the present sections. */
    int weight() {
        return weight;
    }

    /**
     * Reports whether this section is decoded under the configuration. An absent section receives
     * no bytes, so enabling a category that no present section needs leaves the layout unchanged.
     */
    boolean isPresent(IrGenerationConfig config) {
        return requiredCategory.map(category -> !config.ignoredCategories().contains(category)).orElse(true);
    }

    /**
     * Divides every remaining byte of {@code draw} among the present sections, in declaration order.
     *
     * <p>Each present section receives the floor of its proportional share and the last present one
     * also receives the remainder, so no byte is left unowned and the parent cursor ends exhausted.
     * An absent section receives an empty cursor.
     */
    static Map<ModuleSection, Draw> split(Draw draw, IrGenerationConfig config) {
        var total = 0;
        ModuleSection last = null;
        for (var section : values()) {
            if (section.isPresent(config)) {
                total += section.weight;
                last = section;
            }
        }
        var remaining = (long) draw.remaining();
        var result = new EnumMap<ModuleSection, Draw>(ModuleSection.class);
        for (var section : values()) {
            final int length;
            if (!section.isPresent(config)) {
                length = 0;
            } else if (section == last) {
                length = draw.remaining();
            } else {
                length = Math.toIntExact(remaining * section.weight / total);
            }
            result.put(section, draw.slice(length));
        }
        return result;
    }
}
