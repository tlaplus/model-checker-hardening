package io.github.tlaplus.hardening.workflow.input;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.config.PbtConfig;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Property-based candidates (ADR 0002): a target draws one richness cohort, and every candidate for
 * it is a uniformly random byte array of a length from {@link #sampleLength}.
 */
@ArchRole(Role.POLICY)
@Key
public final class PbtCandidates implements CandidateSource {
    private final PbtConfig pbt;

    public PbtCandidates(PbtConfig pbt) {
        this.pbt = Objects.requireNonNull(pbt, "pbt");
    }

    @Override
    public Target claim(RandomGenerator random) {
        var cohort = random.nextInt(pbt.richnessCohorts());
        var threshold = pbt.richnessThreshold(cohort);
        return new Target() {
            @Override
            public Draft draw(RandomGenerator candidateRandom) {
                var input = new byte[sampleLength(candidateRandom, pbt.maximumInputBytes())];
                candidateRandom.nextBytes(input);
                return Draft.generated(input, cohort);
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

    /**
     * Samples a length from a uniformly selected logarithmic size bucket: {@code 0..3},
     * {@code 4..7}, {@code 8..15}, and so on up to {@code maximum}.
     */
    static int sampleLength(RandomGenerator random, int maximum) {
        Objects.requireNonNull(random, "random");
        Preconditions.requireNonnegative(maximum, "maximum");
        if (maximum == 0) {
            return 0;
        }
        var maximumPower = Integer.SIZE - 1 - Integer.numberOfLeadingZeros(maximum);
        var bucketCount = maximum < 4 ? 1 : maximumPower;
        var bucket = bucketCount == 1 ? 0 : random.nextInt(bucketCount);
        var lower = bucket == 0 ? 0 : 1L << (bucket + 1);
        var upper = bucket == 0
                ? Math.min(3, maximum)
                : Math.min((1L << (bucket + 2)) - 1, maximum);
        var width = Math.toIntExact(upper - lower + 1);
        return Math.toIntExact(lower) + (width == 1 ? 0 : random.nextInt(width));
    }
}
