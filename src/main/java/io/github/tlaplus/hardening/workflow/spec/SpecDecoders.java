package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable registry of the decoders that turn stored payloads into checkable modules.
 *
 * <p>Every stage regenerates its artifact from the stored bytes, so which decoder those bytes
 * belong to is a property of the entry rather than of the run. This type owns the mapping and
 * guarantees that every {@link InputKind} has a decoder; callers cannot observe or construct an
 * incomplete production map.
 */
public final class SpecDecoders {
    private final Map<InputKind, Generator<SpecArtifact>> decoders;

    private SpecDecoders(Map<InputKind, Generator<SpecArtifact>> decoders) {
        Objects.requireNonNull(decoders, "decoders");
        var copy = new EnumMap<InputKind, Generator<SpecArtifact>>(InputKind.class);
        copy.putAll(decoders);
        for (var kind : InputKind.values()) {
            if (copy.get(kind) == null) {
                throw new IllegalArgumentException(
                        "no decoder for input kind '" + kind.encodedName() + "'");
            }
        }
        this.decoders = Collections.unmodifiableMap(copy);
    }

    /** Returns a complete decoder registry under one generator configuration. */
    public static SpecDecoders of(IrGenerationConfig config) {
        Objects.requireNonNull(config, "config");
        var decoders = new EnumMap<InputKind, Generator<SpecArtifact>>(InputKind.class);
        decoders.put(
                InputKind.EXPRESSION,
                IrGenerators.expressions(config).map(SpecArtifact::fromExpression));
        decoders.put(
                InputKind.MODULE,
                IrGenerators.specs(config).map(SpecArtifact::fromGeneratedSpec));
        return new SpecDecoders(decoders);
    }

    /** Returns the decoder of {@code kind}; completeness is checked when the registry is built. */
    public Generator<SpecArtifact> decoder(InputKind kind) {
        return decoders.get(Objects.requireNonNull(kind, "kind"));
    }

    /** Decodes one stored input through the decoder named by that input. */
    public SpecArtifact decode(CorpusInput input) {
        Objects.requireNonNull(input, "input");
        return decoder(input.kind()).generate(input.input());
    }

    /**
     * Returns a complete registry with a replacement expression decoder.
     *
     * <p>This is useful when a caller decorates expression generation, and keeps test injection
     * from manufacturing a partial decoder map.
     */
    public SpecDecoders replacingExpressions(Generator<TlaEx> expressions) {
        Objects.requireNonNull(expressions, "expressions");
        var replacements = new EnumMap<InputKind, Generator<SpecArtifact>>(InputKind.class);
        replacements.putAll(decoders);
        replacements.put(
                InputKind.EXPRESSION, expressions.map(SpecArtifact::fromExpression));
        return new SpecDecoders(replacements);
    }
}
