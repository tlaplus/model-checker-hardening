package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.corpus.Technique;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.gen.rewrite.MetamorphicPayload;
import io.github.tlaplus.hardening.gen.rewrite.Rewrite;
import io.github.tlaplus.hardening.gen.rewrite.Rewriter;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.library.LibraryPreparation;
import io.github.tlaplus.hardening.workflow.library.RuleLibraryPreparation;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

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
    /** Decoders of a metamorphic payload's base paired with itself; empty for a conformance registry. */
    private final Map<InputKind, Generator<SpecArtifact>> unrewritten;
    private final OperatorLibrary library;
    private final Manifests manifests;

    /** The replay identities of the prepared custom library and rule library, empty when absent. */
    private record Manifests(String library, String rules) {
        Manifests {
            Objects.requireNonNull(library, "library");
            Objects.requireNonNull(rules, "rules");
        }
    }

    /** The replay identity of the prepared library, empty when none is configured. */
    public String libraryManifest() { return manifests.library(); }

    /** The replay identity of the prepared rewrite rules, empty unless the technique is metamorphic. */
    public String rewriteManifest() { return manifests.rules(); }

    private SpecDecoders(Map<InputKind, Generator<SpecArtifact>> decoders,
            Map<InputKind, Generator<SpecArtifact>> unrewritten, OperatorLibrary library, Manifests manifests) {
        this.library = Objects.requireNonNull(library, "library");
        this.manifests = Objects.requireNonNull(manifests, "manifests");
        this.unrewritten = Map.copyOf(Objects.requireNonNull(unrewritten, "unrewritten"));
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

    /**
     * Loads configured libraries, and the rewrite rules of a metamorphic technique, once before
     * constructing the reusable decoders of {@code technique}.
     */
    public static SpecDecoders prepare(FuzzTlaConfig config, Technique technique) throws WorkflowException {
        try {
            var prepared = LibraryPreparation.prepare(config);
            return switch (technique) {
                case PBT -> of(prepared.generator(), new Manifests(prepared.manifest(), ""));
                case MT -> {
                    var rules = RuleLibraryPreparation.prepare(
                            config.metamorphic(), config.workflow().checker(CorpusStage.APALACHE));
                    if (rules.library().isEmpty()) {
                        throw new WorkflowException("--how=mt needs rewrite rules: set metamorphic.rules");
                    }
                    yield metamorphic(prepared.generator(),
                            new Rewriter(rules.library(), prepared.generator(), config.metamorphic().limits()),
                            new Manifests(prepared.manifest(), rules.manifest()));
                }
            };
        } catch (IllegalArgumentException exception) {
            throw new WorkflowException(
                    "invalid custom generator configuration: " + exception.getMessage(), exception);
        }
    }

    /** Returns a complete conformance decoder registry under one generator configuration. */
    public static SpecDecoders of(IrGenerationConfig config) {
        return of(config, new Manifests("", ""));
    }

    /** Returns a complete metamorphic decoder registry, whose rules {@code rewriter} applies. */
    public static SpecDecoders metamorphic(IrGenerationConfig config, Rewriter rewriter) {
        return metamorphic(config, rewriter, new Manifests("", ""));
    }

    private static SpecDecoders of(IrGenerationConfig config, Manifests manifests) {
        Objects.requireNonNull(config, "config");
        var decoders = new EnumMap<InputKind, Generator<SpecArtifact>>(InputKind.class);
        decoders.put(
                InputKind.EXPRESSION,
                IrGenerators.expressions(config).map(expression -> SpecArtifact.fromExpression(expression, config.library())));
        decoders.put(
                InputKind.MODULE,
                IrGenerators.specs(config).map(spec -> SpecArtifact.fromGeneratedSpec(spec, config.library())));
        return new SpecDecoders(decoders, Map.of(), config.library(), manifests);
    }

    /**
     * Decodes a metamorphic payload (ADR 0016 §1): the base part through the decoder of the kind,
     * the rewrite part through the rewriter. A pair in which no rule applied is rejected, since both
     * sides would be the same module.
     */
    private static SpecDecoders metamorphic(IrGenerationConfig config, Rewriter rewriter, Manifests manifests) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(rewriter, "rewriter");
        var expressions = IrGenerators.expressions(config);
        var specs = IrGenerators.specs(config);
        Function<Rewrite<TlaEx>, SpecArtifact> expressionRelation =
                rewrite -> SpecArtifact.fromExpressionRewrite(rewrite, config.library());
        Function<Rewrite<GeneratedSpec>, SpecArtifact> specRelation =
                rewrite -> SpecArtifact.fromSpecRewrite(rewrite, config.library());
        var decoders = new EnumMap<InputKind, Generator<SpecArtifact>>(InputKind.class);
        decoders.put(InputKind.EXPRESSION, rewriting(expressions, rewriter::rewriteExpression, expressionRelation));
        decoders.put(InputKind.MODULE, rewriting(specs, rewriter::rewriteSpec, specRelation));
        var unrewritten = new EnumMap<InputKind, Generator<SpecArtifact>>(InputKind.class);
        unrewritten.put(InputKind.EXPRESSION, unrewritten(expressions, expressionRelation));
        unrewritten.put(InputKind.MODULE, unrewritten(specs, specRelation));
        return new SpecDecoders(decoders, unrewritten, config.library(), manifests);
    }

    /** Decodes the base part through {@code base} and the rewrite part through {@code rewrite}. */
    private static <T> Generator<SpecArtifact> rewriting(
            Generator<T> base, BiFunction<T, Draw, Rewrite<T>> rewrite, Function<Rewrite<T>, SpecArtifact> relation) {
        return draw -> {
            var parts = MetamorphicPayload.split(draw);
            var pair = rewrite.apply(parts.base().draw(base), parts.rewrite());
            if (pair.isIdentity()) {
                throw new InputRejectedException("no rewrite rule applied");
            }
            return relation.apply(pair);
        };
    }

    /** Decodes the base part through {@code base} and pairs it with itself, ignoring the rewrite part. */
    private static <T> Generator<SpecArtifact> unrewritten(
            Generator<T> base, Function<Rewrite<T>, SpecArtifact> relation) {
        return draw -> relation.apply(Rewrite.identity(MetamorphicPayload.split(draw).base().draw(base)));
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
     * Decodes the base of a metamorphic input and relates it to itself, as if no rule applied
     * (ADR 0016 §4). A checker that reports a counterexample to this relation evaluates two copies
     * of one module differently, so no rule is at fault. The rewrite part is ignored.
     *
     * @throws IllegalStateException for a conformance registry, which decodes no relation
     */
    public SpecArtifact decodeUnrewritten(CorpusInput input) {
        Objects.requireNonNull(input, "input");
        var decoder = unrewritten.get(input.kind());
        if (decoder == null) {
            throw new IllegalStateException("only a metamorphic registry decodes an unrewritten relation");
        }
        return decoder.generate(input.input());
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
        return new SpecDecoders(replacements, unrewritten, library, manifests);
    }
}
