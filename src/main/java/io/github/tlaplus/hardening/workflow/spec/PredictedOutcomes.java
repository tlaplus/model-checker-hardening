package io.github.tlaplus.hardening.workflow.spec;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.ExpectedOutcome;
import io.github.tlaplus.hardening.corpus.ExpectedOutcomes;
import io.github.tlaplus.hardening.gen.graph.GeneratedGraph;
import io.github.tlaplus.hardening.gen.trace.TracePayload;
import java.util.Objects;
import java.util.Optional;

/**
 * Predicts the outcome of an entry whose kind predicts one:
 *
 * <ul>
 *   <li>a graph entry, by replaying it through the decoders (ADR 0023 §4): a counterexample of the
 *       target's distance when a behaviour reaches the target within the step bound, and a pass
 *       otherwise;
 *   <li>a trace entry (ADR 0034 §4): a counterexample of exactly the trace's transitions, which
 *       its payload states without decoding the base.
 * </ul>
 *
 * An entry of another kind predicts nothing.
 */
@ArchRole(Role.ADAPTER)
public final class PredictedOutcomes implements ExpectedOutcomes {
    private final SpecDecoders decoders;

    public PredictedOutcomes(SpecDecoders decoders) {
        this.decoders = Objects.requireNonNull(decoders, "decoders");
    }

    @Override
    public Optional<ExpectedOutcome> expected(CorpusInput input) {
        Objects.requireNonNull(input, "input");
        return switch (input.kind()) {
            case GRAPH -> decoders.decode(input).graph().map(PredictedOutcomes::of);
            case TRACE -> Optional.of(ExpectedOutcome.counterexample(
                    TracePayload.decode(input.input()).trace().transitions()));
            case EXPRESSION, MODULE -> Optional.empty();
        };
    }

    /** Returns the outcome {@code graph} predicts. */
    public static ExpectedOutcome of(GeneratedGraph graph) {
        var distance = graph.targetDistance();
        return distance.isPresent() ? ExpectedOutcome.counterexample(distance.getAsInt()) : ExpectedOutcome.pass();
    }
}
