package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.engine.ApplicativeExpressionKind.ApplicativeType;

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
            var applied = draw.draw(typeFactory.readType(
                    candidate -> applicative.isInstance(candidate)
                            && candidate.components().contains(component),
                    containing(applicative, component)));
            int position = draw.draw(typeFactory.positionOf(applied, component));
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
            var applied = draw.draw(typeFactory.readType(
                    applicative::isInstance,
                    typeFactory.componentType().flatMap(component -> containing(applicative, component))));
            return builder().domain(draw.draw(expression(applied, remainingDepth - 1)));
        };
    }

    /** Draws the other components of a fresh type and where the required one sits among them. */
    private Generator<IrType> containing(ApplicativeType applicative, IrType component) {
        return typeFactory.containing(
                component,
                applicative.maximumOtherComponents(context.config().expressions().collections().maximumSize()),
                components -> applicative.of(components, context));
    }

    /** Returns a record's field name or a tuple's position, or draws a sequence index. */
    private Generator<TlaEx> index(
            ApplicativeType applicative, IrType applied, int position, int remainingDepth) {
        return draw -> applicative.literalIndex(builder(), applied, position)
                .orElseGet(() -> draw.draw(expression(PrimitiveType.INT, remainingDepth - 1)));
    }
}
