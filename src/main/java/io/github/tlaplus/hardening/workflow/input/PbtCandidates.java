package io.github.tlaplus.hardening.workflow.input;

import io.github.tlaplus.hardening.config.PbtConfig;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Property-based candidates (ADR 0002): a target draws one richness cohort, and every candidate for
 * it is a uniformly random byte array of a length from {@link InputLengthSampler}, laid out for the
 * technique's decoders.
 */
public final class PbtCandidates implements CandidateSource {
    private final PbtConfig pbt;
    private final CandidateLayout layout;

    public PbtCandidates(PbtConfig pbt, CandidateLayout layout) {
        this.pbt = Objects.requireNonNull(pbt, "pbt");
        this.layout = Objects.requireNonNull(layout, "layout");
    }

    @Override
    public Target claim(RandomGenerator random) {
        var cohort = random.nextInt(pbt.richnessCohorts());
        var threshold = pbt.richnessThreshold(cohort);
        return new Target() {
            @Override
            public Draft draw(RandomGenerator candidateRandom) {
                var payload = new byte[InputLengthSampler.sample(candidateRandom, pbt.maximumInputBytes())];
                candidateRandom.nextBytes(payload);
                return Draft.generated(layout.encode(payload, candidateRandom), cohort);
            }

            @Override
            public double richnessThreshold() {
                return threshold;
            }

            @Override
            public String describe() {
                return "richness cohort " + cohort + " (threshold " + threshold + ")";
            }
        };
    }
}
