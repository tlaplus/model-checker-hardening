package io.github.tlaplus.hardening.workflow.input;

import io.github.tlaplus.hardening.corpus.Mutation;
import io.github.tlaplus.hardening.mutation.ByteMutator;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Mutants of the parent pool (ADR 0010): every candidate mutates a uniformly drawn parent, keeps its
 * cohort, and is rejected as a clone when it renders to the parent's module. Mutants have no
 * richness threshold.
 */
public final class MutantCandidates implements CandidateSource {
    private final ParentPool parents;
    private final ByteMutator mutator;

    public MutantCandidates(ParentPool parents, ByteMutator mutator) {
        this.parents = Objects.requireNonNull(parents, "parents");
        this.mutator = Objects.requireNonNull(mutator, "mutator");
    }

    @Override
    public Target claim(RandomGenerator random) {
        return new Target() {
            @Override
            public Draft draw(RandomGenerator candidateRandom) {
                var parent = parents.draw(candidateRandom);
                var mutant = mutator.mutate(
                        parent.input(), () -> parents.draw(candidateRandom).input(), candidateRandom);
                return new Draft(
                        mutant.input(),
                        parent.cohort(),
                        Optional.of(new Mutation(parent.digest(), mutant.operators())),
                        parent::rendersAs);
            }

            @Override
            public double richnessThreshold() {
                return 0.0;
            }

            @Override
            public String describe() {
                return "a mutant of a parent pool of " + parents.size() + " entries";
            }
        };
    }
}
