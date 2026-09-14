package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Generator;
import io.vavr.Function3;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import org.apalache_mc.tla.jir.ExpressionPair;
import org.apalache_mc.tla.jir.NamedExpression;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;

/** Shared access to the state and recursive factories used by expression generator families. */
abstract class AbstractExprGenFactory {
    protected final GenerationContext context;
    protected final IrTypeGenFactory typeFactory;
    protected final IrExprGenFactory expressionFactory;

    AbstractExprGenFactory(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory) {
        this.context = context;
        this.typeFactory = typeFactory;
        this.expressionFactory = expressionFactory;
    }

    /** Returns the type-safe, scope-unchecked builder for this run. */
    protected final TlaTypedScopeUncheckedBuilder builder() {
        return context.builder();
    }

    /**
     * Returns a recursive generator of an ordinary operand with the requested type and budget.
     *
     * <p>The operand is drawn in {@link LevelContext#valueOperand()}, so it is never temporal. This
     * is the default for every operand; a form opts out only through {@link #sameLevel} or
     * {@link #atLevel}.
     */
    protected final Generator<TlaEx> expression(
            IrType type, int remainingDepth) {
        return context.asValueOperand(expressionFactory.mkGen(type, remainingDepth));
    }

    /**
     * Returns a generator of an operand drawn in the same level context as the form itself.
     *
     * <p>Only the forms through which a temporal formula may pass use it: {@code ~}, {@code /\},
     * {@code \/} and {@code =>}, the branches of {@code IF}, the body of {@code LET}, and labels. TLC
     * cannot handle a temporal formula under {@code <=>}
     * (<a href="https://github.com/tlaplus/tlaplus/issues/1029">tlaplus/tlaplus#1029</a>) or in a {@code CASE} arm.
     */
    protected final Generator<TlaEx> sameLevel(
            IrType type, int remainingDepth) {
        return expressionFactory.mkGen(type, remainingDepth);
    }

    /** Returns a generator of an operand drawn in an explicitly named level context. */
    protected final Generator<TlaEx> atLevel(
            LevelContext level, IrType type, int remainingDepth) {
        return context.withLevel(level, expressionFactory.mkGen(type, remainingDepth));
    }

    /**
     * Draws the action and then the subscript of {@code [A]_v}, {@code <<A>>_v} or a fairness
     * condition, and combines them in that order. The action is drawn in {@link LevelContext#ACTION}
     * and the subscript, whose type is drawn first, in {@link LevelContext#STATE}.
     */
    protected final Generator<TlaEx> subscripted(
            int remainingDepth, BinaryOperator<TlaEx> operation) {
        return draw -> {
            var action = draw.draw(atLevel(LevelContext.ACTION, PrimitiveType.BOOL, remainingDepth - 1));
            var subscriptType = draw.draw(typeFactory.valueType());
            var subscript = draw.draw(atLevel(LevelContext.STATE, subscriptType, remainingDepth - 1));
            return operation.apply(action, subscript);
        };
    }

    /** Returns a generator of {@code WF_v(A)}, or of {@code SF_v(A)} when {@code strong} holds. */
    protected final Generator<TlaEx> fairness(boolean strong, int remainingDepth) {
        return subscripted(remainingDepth, (action, subscript) -> strong
                ? builder().strongFair(subscript, action)
                : builder().weakFair(subscript, action));
    }

    /** Draws same-typed operands left-to-right before invoking the selected builder operation. */
    protected final Generator<TlaEx> binary(
            IrType type, int depth, BinaryOperator<TlaEx> operation) {
        return binary(expression(type, depth), operation);
    }

    /** Draws two operands from one generator, left to right, and combines them. */
    protected static Generator<TlaEx> binary(
            Generator<TlaEx> operand, BinaryOperator<TlaEx> operation) {
        return draw -> operation.apply(draw.draw(operand), draw.draw(operand));
    }

    /** A fresh name usable as a binder, together with the expression that refers to it. */
    protected record Binding(ScopedName name, TlaEx variable) {}

    /**
     * Creates a binder of the requested type. This consumes no bytes, so a caller may create the
     * binder before drawing anything that must precede the scoped body.
     */
    protected final Binding freshBinding(String prefix, IrType type) {
        var name = context.freshBinding(prefix, type);
        return new Binding(name, builder().name(name.name(), type.toTlaType()));
    }

    /** Returns a generator of an expression drawn with {@code binding} in lexical scope. */
    protected final Generator<TlaEx> scopedBody(
            Binding binding, IrType bodyType, int bodyDepth) {
        return context.withBinding(binding.name(), expression(bodyType, bodyDepth));
    }

    /** Draws an unbounded construct, extending lexical scope only around its body. */
    protected final Generator<TlaEx> unbounded(
            String prefix, IrType variableType, IrType bodyType, int depth,
            BinaryOperator<TlaEx> operation) {
        return draw -> {
            var binding = freshBinding(prefix, variableType);
            return operation.apply(binding.variable(), draw.draw(scopedBody(binding, bodyType, depth)));
        };
    }

    /** Allocates the binder before drawing its domain; only the body sees that binding. */
    protected final Generator<TlaEx> bounded(
            String prefix, IrType variableType, IrType bodyType, int depth,
            Function3<TlaEx, TlaEx, TlaEx, TlaEx> operation) {
        return draw -> {
            var binding = freshBinding(prefix, variableType);
            var domain = draw.draw(expression(new SetType(variableType), depth));
            return operation.apply(binding.variable(), domain, draw.draw(scopedBody(binding, bodyType, depth)));
        };
    }

    /**
     * Draws a construct that binds several names at once, as in {@code [x \in S, y \in T |-> e]}
     * or {@code {e : x \in S, y \in T}}. Unlike nested binders, every domain lies outside the scope
     * of all the construct's names, so all domains are drawn before any name enters scope, and
     * the body sees them all.
     */
    protected final Generator<TlaEx> boundedTogether(
            String prefix, List<IrType> variableTypes, IrType bodyType, int depth,
            BiFunction<TlaEx, ExpressionPair<TlaEx>[], TlaEx> operation) {
        return draw -> {
            var bindings = variableTypes.stream().map(type -> freshBinding(prefix, type)).toList();
            var pairs = new ArrayList<ExpressionPair<TlaEx>>();
            for (var binding : bindings) {
                pairs.add(new ExpressionPair<>(binding.variable(),
                        draw.draw(expression(new SetType(binding.name().type()), depth))));
            }
            var body = draw.draw(context.withBindings(
                    bindings.stream().map(Binding::name).toList(), expression(bodyType, depth)));
            return operation.apply(body, BuilderArrays.pairs(pairs));
        };
    }

    /** Returns a tuple whose components are drawn in declaration order. */
    protected final Generator<TlaEx> tuple(
            TupleType type, Function<IrType, Generator<TlaEx>> component) {
        return draw -> builder().tuple(BuilderArrays.expressions(
                type.elements().stream()
                        .map(element -> draw.draw(component.apply(element)))
                        .toList()));
    }

    /** Returns a record whose field values are drawn in declaration order. */
    protected final Generator<TlaEx> record(
            RecordType type, Function<IrType, Generator<TlaEx>> value) {
        return draw -> builder().record(BuilderArrays.named(
                type.fields().stream()
                        .map(field -> new NamedExpression<>(
                                field.name(), draw.draw(value.apply(field.type()))))
                        .toList()));
    }

    /**
     * Returns the operand array of a collection form: at least one expression of the requested
     * type, up to the configured maximum collection size.
     */
    protected final Generator<TlaEx[]> operands(IrType type, int remainingDepth) {
        return operands(expression(type, remainingDepth));
    }

    /** Returns the operand array of a collection form whose operands come from one generator. */
    protected final Generator<TlaEx[]> operands(Generator<TlaEx> operand) {
        return BasicGenerators.listOf(
                        operand,
                        1,
                        context.config().expressions().maximumCollectionSize())
                .map(BuilderArrays::expressions);
    }
}
