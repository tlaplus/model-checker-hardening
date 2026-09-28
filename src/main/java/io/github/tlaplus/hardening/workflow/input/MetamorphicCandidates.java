package io.github.tlaplus.hardening.workflow.input;

import io.github.tlaplus.hardening.corpus.Mutation;
import io.github.tlaplus.hardening.gen.rewrite.MetamorphicPayload;
import io.github.tlaplus.hardening.mutation.MutationOperator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Adopts elite conformance entries (ADR 0016 §6): every candidate is the payload of a uniformly drawn
 * parent of a pbt corpus behind the metamorphic header, followed by random rewrite bytes. The base
 * stays byte-identical to the parent, so it decodes to the parent's module. A candidate keeps its
 * parent's cohort, records {@code adopt} as its provenance, and has no richness threshold. A pair
 * whose rewrite applies no rule is rejected by the decoder.
 */
public final class MetamorphicCandidates implements CandidateSource {
    /** The most rewrite bytes a candidate carries; a rewrite reads a few bytes per applied rule. */
    static final int MAXIMUM_REWRITE_BYTES = 64;

    private final ParentPool parents;

    public MetamorphicCandidates(ParentPool parents) {
        this.parents = Objects.requireNonNull(parents, "parents");
    }

    @Override
    public Target claim(RandomGenerator random) {
        return new Target() {
            @Override
            public Draft draw(RandomGenerator candidateRandom) {
                var parent = parents.draw(candidateRandom);
                var rewrite = new byte[1 + candidateRandom.nextInt(MAXIMUM_REWRITE_BYTES)];
                candidateRandom.nextBytes(rewrite);
                return new Draft(
                        MetamorphicPayload.encode(parent.input(), rewrite),
                        parent.cohort(),
                        Optional.of(new Mutation(parent.digest(), List.of(MutationOperator.ADOPT))),
                        candidate -> false);
            }

            @Override
            public double richnessThreshold() {
                return 0.0;
            }

            @Override
            public String describe() {
                return "an adoption from a parent pool of " + parents.size() + " entries";
            }
        };
    }
}
