package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import java.util.List;
import java.util.Objects;

/**
 * One assembled module and the exploration depth it asks for.
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
 * @param module the module the tools check
 * @param length transitions a bounded checker should explore, never negative
 * @param generated the expressions this input decoded to, each listed once
 */
public record SpecArtifact(TlaModule module, int length, List<TlaEx> generated) {
    public SpecArtifact {
        Objects.requireNonNull(module, "module");
        generated = List.copyOf(Objects.requireNonNull(generated, "generated"));
        if (length < 0) {
            throw new IllegalArgumentException("length must be nonnegative");
        }
    }
}
