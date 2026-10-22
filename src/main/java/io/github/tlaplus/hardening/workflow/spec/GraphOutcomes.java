package io.github.tlaplus.hardening.workflow.spec;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.ExpectedOutcome;
import io.github.tlaplus.hardening.corpus.ExpectedOutcomes;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.graph.GeneratedGraph;
import java.util.Objects;
import java.util.Optional;

/**
 * Predicts the outcome of a graph entry by replaying it through the decoders (ADR 0023 §4): a
 * counterexample of the target's distance when a behaviour reaches the target within the step
 * bound, and a pass otherwise. An entry of another kind predicts nothing.
 */
@ArchRole(Role.ADAPTER)
public final class GraphOutcomes implements ExpectedOutcomes {
    private final SpecDecoders decoders;

    public GraphOutcomes(SpecDecoders decoders) {
        this.decoders = Objects.requireNonNull(decoders, "decoders");
    }

    @Override
    public Optional<ExpectedOutcome> expected(CorpusInput input) {
        Objects.requireNonNull(input, "input");
        if (input.kind() != InputKind.GRAPH) {
            return Optional.empty();
        }
        return decoders.decode(input).graph().map(GraphOutcomes::of);
    }

    /** Returns the outcome {@code graph} predicts. */
    public static ExpectedOutcome of(GeneratedGraph graph) {
        var distance = graph.targetDistance();
        return distance.isPresent() ? ExpectedOutcome.counterexample(distance.getAsInt()) : ExpectedOutcome.pass();
    }
}
