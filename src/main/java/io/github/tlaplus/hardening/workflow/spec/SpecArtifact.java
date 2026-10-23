package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.graph.GeneratedGraph;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.gen.library.SourceLink;
import io.github.tlaplus.hardening.gen.rewrite.Rewrite;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One assembled module, what its checkers are asked about it, and what its decoder produced.
 *
 * <p>The request belongs to the artifact, not to the stage checking it: an expression input has a
 * single state and asks for no transitions, while a generated module bounds its own step counter
 * and may have a temporal property. It travels with the module so that every renderer produces a
 * {@link io.github.tlaplus.hardening.workflow.worker.ToolInput} carrying it.
 *
 * <p>The generated expressions are listed separately from the module because the module also
 * holds the fixed skeleton around them, and the expression wrapper repeats its one expression in
 * two definitions. Admission policy scores what the decoder actually produced, so a change to the
 * skeleton cannot shift the score of an input already in the corpus.
 *
 * <p>An expression artifact also retains that standalone expression for the CLI's default
 * rendering. A generated module has no single expression that represents it.
 *
 * <p>{@link #module()} is self-contained: Apalache evaluates it, and admission scores and matches
 * it. {@link #source()} is what the parser and TLC evaluate; it differs only when the library links
 * a used module by instance (ADR 0014).
 */
@ArchRole(Role.MODEL)
@Key
public final class SpecArtifact {
    private final TlaModule module;
    private final SourceLink source;
    private final CheckRequest request;
    private final List<TlaEx> generated;
    private final Origin origin;

    /** What a decoder assembled, before the library is linked. */
    private record Parts(TlaModule skeleton, CheckRequest request, List<TlaEx> generated, Origin origin) {}

    /**
     * What the decoder produced besides the module, each part absent ({@code null}) unless the
     * decoder has it: the standalone expression, the metamorphic pair, the control graph.
     */
    private record Origin(TlaEx standaloneExpression, Rewrite<?> rewrite, GeneratedGraph graph) {
        static final Origin MODULE = new Origin(null, null, null);
    }

    private SpecArtifact(Parts parts, OperatorLibrary library) {
        this.source = library.linkSource(parts.skeleton(), parts.generated());
        // Without aliases the source form is the self-contained module; link only once.
        this.module = source.aliases().isEmpty() ? source.module() : library.link(parts.skeleton(), parts.generated());
        this.request = Objects.requireNonNull(parts.request(), "request");
        this.generated = List.copyOf(Objects.requireNonNull(parts.generated(), "generated"));
        this.origin = Objects.requireNonNull(parts.origin(), "origin");
    }

    /** Wraps one generated expression, and the library definitions it uses, in the checker module. */
    public static SpecArtifact fromExpression(TlaEx expression, OperatorLibrary library) {
        Objects.requireNonNull(expression, "expression");
        return new SpecArtifact(new Parts(FuzzInputModule.create(expression),
                CheckRequest.invariant(0), List.of(expression), new Origin(library.close(expression), null, null)), library);
    }

    /**
     * Wraps a metamorphic pair of expressions in the checker module (ADR 0016 §3): the explored
     * side initializes {@code exprValue}, and the invariant asserts the checked side.
     */
    public static SpecArtifact fromExpressionRewrite(Rewrite<TlaEx> rewrite, OperatorLibrary library) {
        Objects.requireNonNull(rewrite, "rewrite");
        var explored = rewrite.explored();
        var checked = rewrite.checked();
        return new SpecArtifact(new Parts(FuzzInputModule.create(explored, checked), CheckRequest.invariant(0),
                List.of(explored, checked), new Origin(library.close(rewrite.original()), rewrite, null)), library);
    }

    /**
     * Assembles the implication relation of a metamorphic pair of modules (ADR 0016 §3), which the
     * checkers also check against its action invariant. The property is not rewritten yet, so it is
     * not checked.
     */
    public static SpecArtifact fromSpecRewrite(Rewrite<GeneratedSpec> rewrite, OperatorLibrary library) {
        Objects.requireNonNull(rewrite, "rewrite");
        var original = rewrite.original();
        var rewritten = rewrite.rewritten();
        var generated = new ArrayList<>(original.generated());
        generated.addAll(rewritten.generated());
        return new SpecArtifact(new Parts(FuzzInputModule.create(original, rewritten, rewrite.orientation()),
                new CheckRequest(original.stepBound(), false, true), generated, new Origin(null, rewrite, null)), library);
    }

    /** Assembles the declarations produced by the whole-module decoder, plus the library it uses. */
    public static SpecArtifact fromGeneratedSpec(GeneratedSpec spec, OperatorLibrary library) {
        Objects.requireNonNull(spec, "spec");
        return new SpecArtifact(new Parts(FuzzInputModule.create(spec),
                new CheckRequest(spec.stepBound(), spec.property().isPresent()), spec.generated(), Origin.MODULE), library);
    }

    /**
     * Assembles a graph module (ADR 0023) as a module input, and keeps its graph for the oracle
     * and for inspection.
     */
    public static SpecArtifact fromGeneratedGraph(GeneratedGraph graph, OperatorLibrary library) {
        Objects.requireNonNull(graph, "graph");
        var spec = graph.spec();
        return new SpecArtifact(new Parts(FuzzInputModule.create(spec),
                new CheckRequest(spec.stepBound(), spec.property().isPresent())
                        .withControlVariables(GeneratedGraph.CONTROL_VARIABLES),
                spec.generated(), new Origin(null, null, graph)), library);
    }

    public TlaModule module() {
        return module;
    }

    public SourceLink source() {
        return source;
    }

    public CheckRequest request() {
        return request;
    }

    /** Returns every definition a tool evaluates directly, which admission scores and matches from. */
    public List<String> entryPoints() {
        return request.actionInvariant() ? FuzzInputModule.RELATION_ENTRY_POINTS : FuzzInputModule.ENTRY_POINTS;
    }

    public List<TlaEx> generated() {
        return generated;
    }

    /** Returns the metamorphic pair this artifact relates, when it came from a metamorphic decoder. */
    public Optional<Rewrite<?>> rewrite() {
        return Optional.ofNullable(origin.rewrite());
    }

    /** Returns the control graph this artifact encodes, when it came from the graph decoder. */
    public Optional<GeneratedGraph> graph() {
        return Optional.ofNullable(origin.graph());
    }

    /** Returns the input expression when this artifact came from the expression decoder. */
    public Optional<TlaEx> standaloneExpression() {
        return Optional.ofNullable(origin.standaloneExpression());
    }
}
