package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
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
import io.github.tlaplus.hardening.gen.engine.EdgeGuards;
import io.github.tlaplus.hardening.gen.graph.GeneratedGraph;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.gen.rewrite.MetamorphicPayload;
import io.github.tlaplus.hardening.gen.rewrite.Rewrite;
import io.github.tlaplus.hardening.gen.rewrite.RewriteLibrary;
import io.github.tlaplus.hardening.gen.rewrite.Rewriter;
import io.github.tlaplus.hardening.gen.rewrite.Tautologies;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import io.github.tlaplus.hardening.workflow.library.LibraryPreparation;
import io.github.tlaplus.hardening.workflow.library.RuleLibraryPreparation;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
@ArchRole(Role.FACADE)
@Key
public final class SpecDecoders {
    private final Map<InputKind, Generator<SpecArtifact>> decoders;
    /** Decoders of a metamorphic payload's base paired with itself; empty for a conformance registry. */
    private final Map<InputKind, Generator<SpecArtifact>> unrewritten;
    private final OperatorLibrary library;
    private final Manifests manifests;

    /** Separates the guard manifest from the rewrite manifest in a metamorphic corpus's rule record. */
    private static final String GUARD_SECTION = "graph-guards\n";

    /**
     * The replay identities of the prepared libraries, empty when absent: the custom library, the
     * rule record the corpus pins, and the edge guards of graph inputs.
     */
    private record Manifests(String library, String rules, String guards) {
        Manifests {
            Objects.requireNonNull(library, "library");
            Objects.requireNonNull(rules, "rules");
            Objects.requireNonNull(guards, "guards");
        }

        /** A conformance registry pins its guards alone. */
        static Manifests conformance(String library, String guards) {
            return new Manifests(library, guards, guards);
        }

        /**
         * A metamorphic registry pins its rewrites, followed by its guards when it has any, so a
         * corpus without guards pins the rewrites alone.
         */
        static Manifests metamorphic(String library, String rewrites, String guards) {
            return new Manifests(library, guards.isEmpty() ? rewrites : rewrites + GUARD_SECTION + guards, guards);
        }
    }

    /** The replay identity of the prepared library, empty when none is configured. */
    public String libraryManifest() { return manifests.library(); }

    /**
     * The replay identity of the prepared rule modules, which {@code .rewrite-library} pins: the
     * edge guards of graph inputs, preceded by the rewrites of a metamorphic technique; empty
     * without either.
     */
    public String rewriteManifest() { return manifests.rules(); }

    /** The replay identity of the edge guards of graph inputs, empty without them. */
    public String guardManifest() { return manifests.guards(); }

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
     * Loads configured libraries, the edge guards of graph inputs, and the rewrite rules of a
     * metamorphic technique once before constructing the reusable decoders of {@code technique}.
     * Every technique may decode graph inputs, so each decodes them with the same guards; a
     * metamorphic graph base therefore decodes as it does in a pbt or gbt corpus.
     */
    public static SpecDecoders prepare(FuzzTlaConfig config, Technique technique) throws WorkflowException {
        try {
            var prepared = LibraryPreparation.prepare(config);
            var apalache = config.workflow().checker(CorpusStage.APALACHE);
            var guardRules = RuleLibraryPreparation.prepare(config.graphGuards(), apalache);
            var guards = guards(guardRules.library());
            return switch (technique) {
                case PBT, GBT -> of(prepared.generator(), guards,
                        Manifests.conformance(prepared.manifest(), guardRules.manifest()));
                case MT -> {
                    var rules = RuleLibraryPreparation.prepare(config.metamorphic().rules(), apalache);
                    if (rules.library().isEmpty()) {
                        throw new WorkflowException("--how=mt needs rewrite rules: set metamorphic.rules");
                    }
                    yield metamorphic(prepared.generator(),
                            new Rewriter(rules.library(), prepared.generator(), config.metamorphic().limits()),
                            guards,
                            Manifests.metamorphic(prepared.manifest(), rules.manifest(), guardRules.manifest()));
                }
            };
        } catch (IllegalArgumentException exception) {
            throw new WorkflowException(
                    "invalid custom generator configuration: " + exception.getMessage(), exception);
        }
    }

    /** Returns a complete conformance decoder registry under one generator configuration. */
    public static SpecDecoders of(IrGenerationConfig config) {
        return of(config, Optional.empty(), Manifests.conformance("", ""));
    }

    /** Returns a complete conformance decoder registry whose graph inputs conjoin {@code guards}. */
    public static SpecDecoders of(IrGenerationConfig config, EdgeGuards guards) {
        return of(config, Optional.of(Objects.requireNonNull(guards, "guards")), Manifests.conformance("", ""));
    }

    /** The edge guards of a prepared rule module; none without one. */
    private static Optional<EdgeGuards> guards(RewriteLibrary rules) throws WorkflowException {
        if (rules.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Tautologies(rules));
        } catch (IllegalArgumentException exception) {
            throw new WorkflowException("graph.rules: " + exception.getMessage(), exception);
        }
    }

    /** Returns a complete metamorphic decoder registry, whose rules {@code rewriter} applies. */
    public static SpecDecoders metamorphic(IrGenerationConfig config, Rewriter rewriter) {
        return metamorphic(config, rewriter, Optional.empty(), Manifests.metamorphic("", "", ""));
    }

    private static SpecDecoders of(IrGenerationConfig config, Optional<EdgeGuards> guards, Manifests manifests) {
        Objects.requireNonNull(config, "config");
        var decoders = new EnumMap<InputKind, Generator<SpecArtifact>>(InputKind.class);
        decoders.put(
                InputKind.EXPRESSION,
                IrGenerators.expressions(config).map(expression -> SpecArtifact.fromExpression(expression, config.library())));
        decoders.put(
                InputKind.MODULE,
                IrGenerators.specs(config).map(spec -> SpecArtifact.fromGeneratedSpec(spec, config.library())));
        decoders.put(InputKind.GRAPH,
                graphs(config, guards).map(graph -> SpecArtifact.fromGeneratedGraph(graph, config.library())));
        return new SpecDecoders(decoders, Map.of(), config.library(), manifests);
    }

    /** The graph decoder, whose edges conjoin {@code guards} when present. */
    private static Generator<GeneratedGraph> graphs(IrGenerationConfig config, Optional<EdgeGuards> guards) {
        return guards.map(edgeGuards -> IrGenerators.graphs(config, edgeGuards))
                .orElseGet(() -> IrGenerators.graphs(config));
    }

    /**
     * Decodes a metamorphic payload (ADR 0016 §1): the base part through the decoder of the kind,
     * the rewrite part through the rewriter. A pair in which no rule applied is rejected, since both
     * sides would be the same module. A graph base, whose edges conjoin {@code guards} when present,
     * is rewritten as the module it decodes to, so its relation is a module relation and its graph
     * predicts nothing.
     */
    private static SpecDecoders metamorphic(
            IrGenerationConfig config, Rewriter rewriter, Optional<EdgeGuards> guards, Manifests manifests) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(rewriter, "rewriter");
        var expressions = IrGenerators.expressions(config);
        var specs = IrGenerators.specs(config);
        var graphSpecs = graphs(config, guards).map(GeneratedGraph::spec);
        Function<Rewrite<TlaEx>, SpecArtifact> expressionRelation =
                rewrite -> SpecArtifact.fromExpressionRewrite(rewrite, config.library());
        Function<Rewrite<GeneratedSpec>, SpecArtifact> specRelation =
                rewrite -> SpecArtifact.fromSpecRewrite(rewrite, config.library());
        var decoders = new EnumMap<InputKind, Generator<SpecArtifact>>(InputKind.class);
        decoders.put(InputKind.EXPRESSION, rewriting(expressions, rewriter::rewriteExpression, expressionRelation));
        decoders.put(InputKind.MODULE, rewriting(specs, rewriter::rewriteSpec, specRelation));
        decoders.put(InputKind.GRAPH, rewriting(graphSpecs, rewriter::rewriteSpec, specRelation));
        var unrewritten = new EnumMap<InputKind, Generator<SpecArtifact>>(InputKind.class);
        unrewritten.put(InputKind.EXPRESSION, unrewritten(expressions, expressionRelation));
        unrewritten.put(InputKind.MODULE, unrewritten(specs, specRelation));
        unrewritten.put(InputKind.GRAPH, unrewritten(graphSpecs, specRelation));
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
