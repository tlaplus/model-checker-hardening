package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Factory for deferred, type-directed expression generators within one generation run. */
final class IrExprGenFactory {
    /** Fixed width of one nonterminal form selection, in bytes. */
    static final int SELECTION_BYTES = 2;

    private final GenerationContext context;
    private final IrTypeGenFactory typeFactory;
    private final Map<IrType, List<ExpressionKind>> typeApplicableForms = new HashMap<>();
    private final GeneralExprGenFactory generalFactory;
    private final BooleanExprGenFactory booleanFactory;
    private final IntegerExprGenFactory integerFactory;
    private final SetExprGenFactory setFactory;
    private final SequenceExprGenFactory sequenceFactory;
    private final OtherExprGenFactory otherFactory;

    IrExprGenFactory(GenerationContext context, IrTypeGenFactory typeFactory) {
        this.context = context;
        this.typeFactory = typeFactory;
        otherFactory = new OtherExprGenFactory(context, typeFactory, this);
        generalFactory = new GeneralExprGenFactory(context, typeFactory, this, otherFactory);
        booleanFactory = new BooleanExprGenFactory(context, typeFactory, this);
        integerFactory = new IntegerExprGenFactory(context, typeFactory, this);
        setFactory = new SetExprGenFactory(context, typeFactory, this);
        sequenceFactory = new SequenceExprGenFactory(context, typeFactory, this);
    }

    /**
     * Returns a generator that draws one index across all forms applicable to its type.
     *
     * <p>Calling this factory consumes no bytes and does not increment the node counter. Those
     * effects occur only when the returned generator is invoked.
     *
     * <p>Configured category exclusions, type applicability, and the current lexical scope are
     * evaluated before the index is drawn, and only the selected form is built. Each applicable
     * form occupies as many slots as its weight, so a configured weight of {@code n} makes a form
     * {@code n} times as likely as an unweighted one applicable to the same request; a weight of
     * zero means the form cannot be used here at all. The index has a fixed width of
     * {@link #SELECTION_BYTES} bytes, so a nonterminal selection costs the same regardless of how
     * many slots happen to be in play. Deriving that width from the slot total instead would let a
     * change in type, lexical scope, or weight reframe every byte after the choice. Modulo
     * reduction maps the values of those bytes round-robin over the slots, assigning each either
     * the floor or ceiling of its share. Rejection sampling is intentionally avoided because its
     * variable consumption would make mutation-fuzzer inputs sensitive to preceding choices.
     *
     * <p>A type with exactly one applicable form is dispatched without a draw. Nothing is being
     * chosen there, so spending bytes on it would only shift the rest of the input.
     */
    Generator<TlaEx> mkGen(IrType type, int remainingDepth) {
        return draw -> {
            if (!typeFactory.isEnabled(type)) {
                throw new IllegalStateException(
                        "expression type uses an ignored category: " + type);
            }
            if (remainingDepth <= 0 || !context.consumeNode() || draw.isEmpty()) {
                return draw.draw(generalFactory.terminal(type));
            }

            // Only the weight can change between draws, so the rest is cached per type.
            var candidates = typeApplicableForms(type);
            var slotTotal = 0;
            var applicableCount = 0;
            ExpressionKind onlyApplicable = null;
            for (var kind : candidates) {
                var weight = kind.selectionWeight(context, type);
                if (weight > 0) {
                    slotTotal += weight;
                    applicableCount++;
                    onlyApplicable = kind;
                }
            }
            if (applicableCount == 0) {
                throw new InputRejectedException(
                        "no expression form can produce type " + type);
            }
            if (applicableCount == 1) {
                return draw.draw(mkGen(onlyApplicable, type, remainingDepth));
            }

            var selected = draw.drawIndex(slotTotal, SELECTION_BYTES);
            for (var kind : candidates) {
                var weight = kind.selectionWeight(context, type);
                if (selected < weight) {
                    return draw.draw(mkGen(kind, type, remainingDepth));
                }
                selected -= weight;
            }
            throw new IllegalStateException("unreachable expression choice");
        };
    }

    /** Reports whether a form is enabled and its type and scope requirements are satisfied. */
    boolean isApplicable(ExpressionKind kind, IrType type) {
        return typeApplicableForms(type).contains(kind)
                && kind.selectionWeight(context, type) > 0;
    }

    /** Returns the selection slots a form occupies for a type, or zero when it cannot be used. */
    int selectionWeight(ExpressionKind kind, IrType type) {
        return typeApplicableForms(type).contains(kind)
                ? kind.selectionWeight(context, type)
                : 0;
    }

    /**
     * Returns the forms this run may use for a type, in catalog order. Configured exclusions and
     * type applicability depend only on the type, so the answer is computed once per type; lexical
     * scope is checked on every draw.
     */
    private List<ExpressionKind> typeApplicableForms(IrType type) {
        if (!typeFactory.isEnabled(type)) {
            return List.of();
        }
        return typeApplicableForms.computeIfAbsent(
                type,
                requested -> ExpressionKindCatalog.all().stream()
                        .filter(kind ->
                                !kind.isUnavailableWith(context.config().ignoredCategories()))
                        .filter(kind -> kind.isTypeApplicable(requested))
                        .toList());
    }

    /** Returns the generator supplied by a selected kind's typed family. */
    private Generator<TlaEx> mkGen(
            ExpressionKind kind, IrType type, int remainingDepth) {
        return switch (kind) {
            case GeneralExpressionKind general ->
                generalFactory.mkGen(general, type, remainingDepth);
            case BooleanExpressionKind bool -> booleanFactory.mkGen(bool, remainingDepth);
            case IntegerExpressionKind integer -> integerFactory.mkGen(integer, remainingDepth);
            case SetExpressionKind set ->
                setFactory.mkGen(set, (SetType) type, remainingDepth);
            case SequenceExpressionKind sequence ->
                sequenceFactory.mkGen(sequence, (SequenceType) type, remainingDepth);
            case OtherExpressionKind other ->
                otherFactory.mkGen(other, type, remainingDepth);
        };
    }
}
