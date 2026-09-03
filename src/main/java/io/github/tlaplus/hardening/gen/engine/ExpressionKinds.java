package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.ArrayList;
import java.util.List;

/**
 * The catalog of every expression form, in decoder order.
 *
 * <p>This order <em>is</em> the byte encoding of a nonterminal choice: family order and each
 * enum's declaration order decide which form a stored input decodes to. Reordering a family or an
 * enum constant therefore reinterprets every corpus entry ever written. {@code ExpressionKindsTest}
 * pins the order so such a change fails a test instead of silently changing the corpus.
 *
 * <p>The catalog is the upper bound on how many forms one selection can address, so it must stay
 * within {@link #MAXIMUM_SELECTION_SLOTS}.
 */
public final class ExpressionKinds {
    /** Selection slots addressable by one fixed-width index. */
    static final int MAXIMUM_SELECTION_SLOTS = 1 << (IrExprGenFactory.SELECTION_BYTES * Byte.SIZE);

    private static final List<ExpressionKind> ALL = buildCatalog();

    static {
        if (ALL.size() > MAXIMUM_SELECTION_SLOTS) {
            throw new ExceptionInInitializerError(
                    "expression kinds must fit in a fixed-width choice");
        }
    }

    private ExpressionKinds() {}

    /**
     * Checks that no request can present more slots than one index can address.
     *
     * <p>The catalog bounds how many forms a single request can offer, so the catalog size plus
     * the slots the configured weights add is the worst case over every type and scope.
     *
     * @throws IllegalArgumentException if the configured weights exceed the addressable slots
     */
    static void requireAddressableSlots(IrGenerationConfig config) {
        var worstCase = (long) ALL.size() + config.additionalSelectionSlots();
        if (worstCase > MAXIMUM_SELECTION_SLOTS) {
            throw new IllegalArgumentException(
                    "configured weights need " + worstCase + " selection slots, but only "
                            + MAXIMUM_SELECTION_SLOTS + " are addressable");
        }
    }

    /**
     * Returns every form in decoder order. Family order and each enum's declaration order are the
     * implementation-local byte encoding. The catalog is built once, not during expression draws.
     */
    public static List<ExpressionKind> all() {
        return ALL;
    }

    /** Concatenates the family enums once in their documented decoder order. */
    private static List<ExpressionKind> buildCatalog() {
        var result = new ArrayList<ExpressionKind>();
        result.addAll(List.of(GeneralExpressionKind.values()));
        result.addAll(List.of(BooleanExpressionKind.values()));
        result.addAll(List.of(IntegerExpressionKind.values()));
        result.addAll(List.of(SetExpressionKind.values()));
        result.addAll(List.of(SequenceExpressionKind.values()));
        result.addAll(List.of(OtherExpressionKind.values()));
        return List.copyOf(result);
    }
}
