package io.github.tlaplus.hardening.gen.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * The catalog of every expression form, in decoder order.
 *
 * <p>This order <em>is</em> the byte encoding of a nonterminal choice: family order and each
 * enum's declaration order decide which form a stored input decodes to. Reordering a family or an
 * enum constant therefore reinterprets every corpus entry ever written. {@code ExpressionKindsTest}
 * pins the order so such a change fails a test instead of silently changing the corpus.
 */
final class ExpressionKinds {
    private static final int SINGLE_BYTE_CHOICE_COUNT = 1 << Byte.SIZE;
    private static final List<ExpressionKind> ALL = buildCatalog();

    static {
        if (ALL.size() > SINGLE_BYTE_CHOICE_COUNT) {
            throw new ExceptionInInitializerError(
                    "expression kinds must fit in a single-byte choice");
        }
    }

    private ExpressionKinds() {}

    /**
     * Returns every form in decoder order. Family order and each enum's declaration order are the
     * implementation-local byte encoding. The catalog is built once, not during expression draws.
     */
    static List<ExpressionKind> all() {
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
