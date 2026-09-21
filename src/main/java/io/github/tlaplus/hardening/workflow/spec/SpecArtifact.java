package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.gen.library.SourceLink;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
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
public final class SpecArtifact {
    private final TlaModule module;
    private final SourceLink source;
    private final CheckRequest request;
    private final List<TlaEx> generated;
    private final TlaEx standaloneExpression;

    private SpecArtifact(
            TlaModule skeleton,
            OperatorLibrary library,
            CheckRequest request,
            List<TlaEx> generated,
            TlaEx standaloneExpression) {
        this.source = library.linkSource(skeleton, generated);
        // Without aliases the source form is the self-contained module; link only once.
        this.module = source.aliases().isEmpty() ? source.module() : library.link(skeleton, generated);
        this.request = Objects.requireNonNull(request, "request");
        this.generated = List.copyOf(Objects.requireNonNull(generated, "generated"));
        this.standaloneExpression = standaloneExpression;
    }

    /** Wraps one generated expression, and the library definitions it uses, in the checker module. */
    public static SpecArtifact fromExpression(TlaEx expression, OperatorLibrary library) {
        Objects.requireNonNull(expression, "expression");
        return new SpecArtifact(FuzzInputModule.create(expression), library,
                CheckRequest.invariant(0), List.of(expression), library.close(expression));
    }

    /** Assembles the declarations produced by the whole-module decoder, plus the library it uses. */
    public static SpecArtifact fromGeneratedSpec(GeneratedSpec spec, OperatorLibrary library) {
        Objects.requireNonNull(spec, "spec");
        return new SpecArtifact(FuzzInputModule.create(spec), library,
                new CheckRequest(spec.stepBound(), spec.property().isPresent()), spec.generated(), null);
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

    public List<TlaEx> generated() {
        return generated;
    }

    /** Returns the input expression when this artifact came from the expression decoder. */
    public Optional<TlaEx> standaloneExpression() {
        return Optional.ofNullable(standaloneExpression);
    }
}
