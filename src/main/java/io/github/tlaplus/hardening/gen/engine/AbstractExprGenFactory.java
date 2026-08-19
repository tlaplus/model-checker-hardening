package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Generator;
import java.util.function.Function;
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

    /** Returns a recursive expression generator with the requested type and budget. */
    protected final Generator<TlaEx> expression(
            IrType type, int remainingDepth) {
        return expressionFactory.mkGen(type, remainingDepth);
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
        return BasicGenerators.listOf(
                        expression(type, remainingDepth),
                        1,
                        context.config().maximumCollectionSize())
                .map(BuilderArrays::expressions);
    }
}
