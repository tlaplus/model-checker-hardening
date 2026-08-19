package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Factory for deferred, type-directed expression generators within one generation run. */
final class IrExprGenFactory {
    private final GenerationContext context;
    private final IrTypeGenFactory typeFactory;
    private final Map<IrType, List<ExpressionKind>> typeApplicableForms = new HashMap<>();
    private final GeneralExprGenFactory generalFactory;
    private final BooleanExprGenFactory booleanFactory;
    private final IntegerExprGenFactory integerFactory;
    private final SetExprGenFactory setFactory;
    private final SequenceExprGenFactory sequenceFactory;
    private final OtherExprGenFactory otherFactory;
    private int nodeCount;

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
     * <p>Configured category exclusions, type applicability, and current lexical-scope
     * applicability are evaluated before the index is drawn, and only the selected form is built.
     * The catalog contains at most 256 entries, so every nonterminal selection consumes exactly one
     * byte. Modulo reduction maps the 256 byte values round-robin over the applicable forms,
     * assigning each form either the floor or ceiling of {@code 256 / formCount} values. Rejection
     * sampling is intentionally avoided because its variable consumption would make
     * mutation-fuzzer inputs sensitive to preceding choices.
     */
    Generator<TlaEx> mkGen(IrType type, int remainingDepth) {
        return draw -> {
            if (!typeFactory.isEnabled(type)) {
                throw new IllegalStateException(
                        "expression type uses an ignored category: " + type);
            }
            if (remainingDepth <= 0
                    || nodeCount++ >= context.config().maximumNodes()
                    || draw.isEmpty()) {
                return draw.draw(generalFactory.terminal(type));
            }

            // Only scope applicability can change between draws, so the rest is cached per type.
            var candidates = typeApplicableForms(type);
            var formCount = 0;
            for (var kind : candidates) {
                if (kind.isScopeApplicable(context, type)) {
                    formCount++;
                }
            }
            if (formCount == 0) {
                throw new InputRejectedException(
                        "no expression form can produce type " + type);
            }

            var selected = Math.toIntExact(draw.drawLong(0, formCount - 1L));
            for (var kind : candidates) {
                if (kind.isScopeApplicable(context, type) && selected-- == 0) {
                    return draw.draw(mkGen(kind, type, remainingDepth));
                }
            }
            throw new IllegalStateException("unreachable expression choice");
        };
    }

    /** Reports whether a form is enabled and its type and scope requirements are satisfied. */
    boolean isApplicable(ExpressionKind kind, IrType type) {
        return typeApplicableForms(type).contains(kind)
                && kind.isScopeApplicable(context, type);
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
                requested -> ExpressionKinds.all().stream()
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
