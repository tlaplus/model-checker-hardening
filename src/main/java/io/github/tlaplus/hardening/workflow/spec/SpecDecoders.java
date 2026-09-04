package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The decoder that turns one kind of stored payload into a checkable module.
 *
 * <p>Every stage regenerates its artifact from the stored bytes, so which decoder those bytes
 * belong to is a property of the entry rather than of the run: a corpus may hold entries of more
 * than one kind. This is the one place that pairs a kind with its decoder, so a further kind is
 * added here and nowhere else.
 */
public final class SpecDecoders {
    private SpecDecoders() {}

    /**
     * Returns the decoder of every input kind, under one generator configuration.
     *
     * <p>The result covers {@link InputKind} completely, so a caller looks a kind up where it
     * needs the decoder rather than guarding each use. Adding a kind without a decoder here fails
     * at the first lookup of this map instead of returning {@code null} somewhere downstream.
     *
     * @throws IllegalStateException if a kind has no decoder
     */
    public static Map<InputKind, Generator<SpecArtifact>> of(IrGenerationConfig config) {
        Objects.requireNonNull(config, "config");
        var decoders = fromExpressions(IrGenerators.expressions(config));
        decoders.put(
                InputKind.MODULE,
                IrGenerators.specs(config)
                        .map(spec -> new SpecArtifact(
                                FuzzInputModule.create(spec),
                                spec.stepBound(),
                                spec.generated())));
        for (var kind : InputKind.values()) {
            if (!decoders.containsKey(kind)) {
                throw new IllegalStateException(
                        "no decoder for input kind '" + kind.encodedName() + "'");
            }
        }
        return decoders;
    }

    /**
     * Returns the expression decoder alone, built on the supplied expression generator.
     *
     * <p>The result is deliberately partial: it serves a caller that consumes expression inputs
     * only. An expression input is wrapped in the single-state module, which has one state and so
     * asks for no transitions.
     */
    public static EnumMap<InputKind, Generator<SpecArtifact>> fromExpressions(
            Generator<TlaEx> expressions) {
        Objects.requireNonNull(expressions, "expressions");
        var decoders = new EnumMap<InputKind, Generator<SpecArtifact>>(InputKind.class);
        decoders.put(
                InputKind.EXPRESSION,
                expressions.map(expression -> new SpecArtifact(
                        FuzzInputModule.create(expression), 0, List.of(expression))));
        return decoders;
    }
}
