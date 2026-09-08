package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.library.LibraryPreparation;
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
    private final OperatorLibrary library;
    private final String libraryManifest;

    /** The replay identity of the prepared library, empty when none is configured. */
    public String libraryManifest() { return libraryManifest; }

    private SpecDecoders(Map<InputKind, Generator<SpecArtifact>> decoders, OperatorLibrary library,
            String libraryManifest) {
        this.library = Objects.requireNonNull(library, "library");
        this.libraryManifest = Objects.requireNonNull(libraryManifest, "libraryManifest");
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

    /** Loads configured libraries once before constructing the reusable decoders. */
    public static SpecDecoders prepare(FuzzTlaConfig config) throws WorkflowException {
        try {
            var prepared = LibraryPreparation.prepare(config);
            return of(prepared.generator(), prepared.manifest());
        } catch (IllegalArgumentException exception) {
            throw new WorkflowException(
                    "invalid custom generator configuration: " + exception.getMessage(), exception);
        }
    }

    /** Returns a complete decoder registry under one generator configuration. */
    public static SpecDecoders of(IrGenerationConfig config) {
        return of(config, "");
    }

    private static SpecDecoders of(IrGenerationConfig config, String libraryManifest) {
        Objects.requireNonNull(config, "config");
        var decoders = new EnumMap<InputKind, Generator<SpecArtifact>>(InputKind.class);
        decoders.put(
                InputKind.EXPRESSION,
                IrGenerators.expressions(config).map(expression -> SpecArtifact.fromExpression(expression, config.library())));
        decoders.put(
                InputKind.MODULE,
                IrGenerators.specs(config).map(spec -> SpecArtifact.fromGeneratedSpec(spec, config.library())));
        return new SpecDecoders(decoders, config.library(), libraryManifest);
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
                InputKind.EXPRESSION, expressions.map(expression -> SpecArtifact.fromExpression(expression, library)));
        return new SpecDecoders(replacements, library, libraryManifest);
    }
}
