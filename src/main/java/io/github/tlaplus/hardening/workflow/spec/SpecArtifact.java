package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One assembled module, the exploration depth it asks for, and what its decoder produced.
 *
 * <p>The depth belongs to the artifact, not to the stage checking it: an expression input has a
 * single state and asks for no transitions, while a generated module bounds its own step counter.
 * It travels with the module so that every renderer produces a {@link
 * io.github.tlaplus.hardening.workflow.worker.ToolInput} carrying it.
 *
 * <p>The generated expressions are listed separately from the module because the module also
 * holds the fixed skeleton around them, and the expression wrapper repeats its one expression in
 * two definitions. Admission policy scores what the decoder actually produced, so a change to the
 * skeleton cannot shift the score of an input already in the corpus.
 *
 * <p>An expression artifact also retains that standalone expression for the CLI's default
 * rendering. A generated module has no single expression that represents it.
 */
public final class SpecArtifact {
    private final TlaModule module;
    private final int length;
    private final List<TlaEx> generated;
    private final TlaEx standaloneExpression;

    private SpecArtifact(
            TlaModule module,
            int length,
            List<TlaEx> generated,
            TlaEx standaloneExpression) {
        this.module = Objects.requireNonNull(module, "module");
        Preconditions.requireNonnegative(length, "length");
        this.length = length;
        this.generated = List.copyOf(Objects.requireNonNull(generated, "generated"));
        this.standaloneExpression = standaloneExpression;
    }

    /** Wraps one generated expression in the single-state checker module. */
    public static SpecArtifact fromExpression(TlaEx expression) {
        return fromExpression(expression, OperatorLibrary.empty());
    }

    public static SpecArtifact fromExpression(TlaEx expression, OperatorLibrary library) {
        Objects.requireNonNull(expression, "expression");
        return new SpecArtifact(
                library.link(FuzzInputModule.create(expression), List.of(expression)),
                0, List.of(expression), library.close(expression));
    }

    /** Assembles the declarations produced by the whole-module decoder. */
    public static SpecArtifact fromGeneratedSpec(GeneratedSpec spec) {
        return fromGeneratedSpec(spec, OperatorLibrary.empty());
    }

    public static SpecArtifact fromGeneratedSpec(GeneratedSpec spec, OperatorLibrary library) {
        Objects.requireNonNull(spec, "spec");
        return new SpecArtifact(
                library.link(FuzzInputModule.create(spec), spec.generated()),
                spec.stepBound(), spec.generated(), null);
    }

    public TlaModule module() {
        return module;
    }

    public int length() {
        return length;
    }

    public List<TlaEx> generated() {
        return generated;
    }

    /** Returns the input expression when this artifact came from the expression decoder. */
    public Optional<TlaEx> standaloneExpression() {
        return Optional.ofNullable(standaloneExpression);
    }
}
