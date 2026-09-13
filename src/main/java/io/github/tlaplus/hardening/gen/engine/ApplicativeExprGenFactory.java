package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.BasicGenerators;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.engine.ApplicativeExpressionKind.ApplicativeType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/** Constructs reads, updates and domains of records, tuples and sequences. */
final class ApplicativeExprGenFactory extends AbstractExprGenFactory {
    ApplicativeExprGenFactory(
            GenerationContext context,
            IrTypeGenFactory typeFactory,
            IrExprGenFactory expressionFactory) {
        super(context, typeFactory, expressionFactory);
    }

    /** Returns a generator for the selected applicative form. */
    Generator<TlaEx> mkGen(ApplicativeExpressionKind kind, IrType type, int remainingDepth) {
        var applicative = kind.applicativeType();
        return switch (kind.operation()) {
            case ACCESS -> access(applicative, type, remainingDepth);
            case EXCEPT -> except(applicative, type, remainingDepth);
            case DOMAIN -> domain(applicative, remainingDepth);
        };
    }

    /** Draws a type holding the requested component, then which such component to read. */
    private Generator<TlaEx> access(ApplicativeType applicative, IrType component, int remainingDepth) {
        return draw -> {
            var applied = draw.draw(appliedType(
                    applicative,
                    candidate -> candidate.components().contains(component),
                    containing(applicative, component)));
            var positions = IntStream.range(0, applied.components().size())
                    .filter(position -> applied.components().get(position).equals(component))
                    .boxed()
                    .toList();
            var position = draw.choose(positions);
            var source = draw.draw(expression(applied, remainingDepth - 1));
            var index = draw.draw(index(applicative, applied, position, remainingDepth));
            return builder().funApply(source, index);
        };
    }

    /** Draws which component of a value of the requested type to replace, then its replacement. */
    private Generator<TlaEx> except(ApplicativeType applicative, IrType applied, int remainingDepth) {
        return draw -> {
            var components = applied.components();
            var position = (int) draw.drawLong(0, components.size() - 1L);
            var source = draw.draw(expression(applied, remainingDepth - 1));
            var index = draw.draw(index(applicative, applied, position, remainingDepth));
            var replacement = draw.draw(context.withinExceptReplacement(
                    expression(components.get(position), remainingDepth - 1)));
            return builder().except(source, index, replacement);
        };
    }

    /** Draws any type of the requested kind and takes the domain of a value of it. */
    private Generator<TlaEx> domain(ApplicativeType applicative, int remainingDepth) {
        return draw -> {
            var applied = draw.draw(appliedType(
                    applicative,
                    candidate -> true,
                    componentType().flatMap(component -> containing(applicative, component))));
            return builder().domain(draw.draw(expression(applied, remainingDepth - 1)));
        };
    }

    /**
     * Chooses the type of the applied value: after one Boolean, spent whatever follows, either a
     * type reachable from the bindings in scope or a fresh one.
     *
     * <p>Record field names are fresh for every drawn record type, so a fresh type is almost never
     * the type of a visible name, and the expression then requested for it is almost always a
     * literal built on the spot. A reachable type is one that a name, or a read of a name, can
     * supply.
     */
    private Generator<IrType> appliedType(
            ApplicativeType applicative, Predicate<IrType> accepts, Generator<IrType> fresh) {
        return draw -> {
            var fromScope = draw.drawBoolean();
            var candidates = context.reachableTypes().stream()
                    .filter(applicative::isInstance)
                    .filter(accepts)
                    .filter(typeFactory::isEnabled)
                    .toList();
            return fromScope && !candidates.isEmpty()
                    ? draw.choose(candidates)
                    : draw.draw(fresh);
        };
    }

    /** Draws the other components of a fresh type and where the required one sits among them. */
    private Generator<IrType> containing(ApplicativeType applicative, IrType component) {
        return draw -> {
            var others = draw.draw(BasicGenerators.listOf(
                    componentType(),
                    0,
                    applicative.maximumOtherComponents(
                            context.config().expressions().maximumCollectionSize())));
            var components = new ArrayList<IrType>(others);
            components.add((int) draw.drawLong(0, others.size()), component);
            return applicative.of(List.copyOf(components), context);
        };
    }

    /** A component type one level below the configured type depth, like a drawn record's. */
    private Generator<IrType> componentType() {
        return typeFactory.valueType(
                Math.max(0, context.config().expressions().maximumTypeDepth() - 1));
    }

    /** Returns a record's field name or a tuple's position, or draws a sequence index. */
    private Generator<TlaEx> index(
            ApplicativeType applicative, IrType applied, int position, int remainingDepth) {
        return draw -> applicative.literalIndex(builder(), applied, position)
                .orElseGet(() -> draw.draw(expression(PrimitiveType.INT, remainingDepth - 1)));
    }
}
