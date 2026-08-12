package io.github.tlaplus.hardening.gen.engine;

import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import org.apalache_mc.tla.jir.TlaBuilderExpr;

/** Factory for deferred, type-directed expression generators within one generation run. */
final class IrExprGenFactory {
    private final GenerationContext context;
    private final GeneralExprGenFactory generalFactory;
    private final BooleanExprGenFactory booleanFactory;
    private final IntegerExprGenFactory integerFactory;
    private final SetExprGenFactory setFactory;
    private final SequenceExprGenFactory sequenceFactory;
    private final OtherExprGenFactory otherFactory;
    private int nodeCount;

    IrExprGenFactory(GenerationContext context, IrTypeGenFactory typeFactory) {
        this.context = context;
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
     * <p>Type and current lexical-scope applicability are counted before the index is drawn, and
     * only the selected form is built. The catalog contains at most 256 entries, so every
     * nonterminal selection consumes exactly one byte. Modulo reduction maps the 256 byte values
     * round-robin over the applicable forms, assigning each form either the floor or ceiling of
     * {@code 256 / formCount} values. Rejection sampling is intentionally avoided because its
     * variable consumption would make mutation-fuzzer inputs sensitive to preceding choices.
     */
    Generator<TlaBuilderExpr> mkGen(IrType type, int remainingDepth) {
        return draw -> {
            if (remainingDepth <= 0
                    || nodeCount++ >= context.config().maximumNodes()
                    || draw.isEmpty()) {
                return draw.draw(generalFactory.terminal(type));
            }

            var formCount = 0;
            for (var kind : ExpressionKinds.all()) {
                if (isApplicable(kind, type)) {
                    formCount++;
                }
            }
            if (formCount == 0) {
                throw new InputRejectedException(
                        "no expression form can produce type " + type);
            }

            var selected = Math.toIntExact(draw.drawLong(0, formCount - 1L));
            for (var kind : ExpressionKinds.all()) {
                if (isApplicable(kind, type) && selected-- == 0) {
                    return draw.draw(mkGen(kind, type, remainingDepth));
                }
            }
            throw new IllegalStateException("unreachable expression choice");
        };
    }

    /** Reports whether a form's type and lexical-scope requirements are currently satisfied. */
    boolean isApplicable(ExpressionKind kind, IrType type) {
        if (!kind.isTypeApplicable(type)) {
            return false;
        }
        if (kind == GeneralExpressionKind.NAME) {
            return context.hasBinding(type);
        }
        if (kind == GeneralExpressionKind.OPERATOR_APPLICATION) {
            return context.hasOperatorReturning(type);
        }
        return true;
    }

    /** Returns the generator supplied by a selected kind's typed family. */
    private Generator<TlaBuilderExpr> mkGen(
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
