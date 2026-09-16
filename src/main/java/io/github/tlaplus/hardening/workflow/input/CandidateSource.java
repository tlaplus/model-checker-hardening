package io.github.tlaplus.hardening.workflow.input;

import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.corpus.Mutation;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Where the candidates for a target entry of the input stage come from: property-based generation
 * or mutation of a selected parent (ADR 0010).
 *
 * <p>A source only proposes bytes. Decoding, admission, deduplication, quarantine and storage are
 * the input stage's, and are the same for every source.
 */
public interface CandidateSource {
    /** Claims one target entry, fixing what every candidate drawn for it shares. */
    Target claim(RandomGenerator random);

    /** One claimed target entry. */
    interface Target {
        /** Draws one candidate. */
        Draft draw(RandomGenerator random);

        /** Returns the collection-richness score a candidate for this target must reach. */
        double richnessThreshold();

        /** Describes the target in the diagnostic of a target that cannot be filled. */
        String describe();
    }

    /**
     * One undecoded candidate and the admission metadata it carries.
     *
     * @param cohort the richness cohort the candidate is recorded under
     * @param mutation how the candidate was derived, for a mutant
     * @param isClone whether a decoded candidate is its parent's module again
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    record Draft(
            byte[] input, int cohort, Optional<Mutation> mutation, Predicate<SpecArtifact> isClone) {
        public Draft {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(mutation, "mutation");
            Objects.requireNonNull(isClone, "isClone");
        }

        /** Returns a candidate that no parent was mutated into. */
        static Draft generated(byte[] input, int cohort) {
            return new Draft(input, cohort, Optional.empty(), _ -> false);
        }

        /** Returns the metadata the candidate is stored with once it is admitted. */
        GenerationMetadata metadata(int generation, double richness) {
            return mutation
                    .map(value -> GenerationMetadata.mutated(generation, cohort, richness, value))
                    .orElseGet(() -> GenerationMetadata.generated(generation, cohort, richness));
        }
    }
}
