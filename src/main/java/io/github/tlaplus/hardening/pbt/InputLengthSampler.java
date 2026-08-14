package io.github.tlaplus.hardening.pbt;

import java.util.Objects;
import java.util.random.RandomGenerator;

/** Samples bounded lengths from uniformly selected logarithmic size buckets. */
public final class InputLengthSampler {
    private InputLengthSampler() {}

    /** Chooses among {@code 0..3}, {@code 4..7}, {@code 8..15}, and so on. */
    public static int sample(RandomGenerator random, int maximum) {
        Objects.requireNonNull(random, "random");
        if (maximum < 0) {
            throw new IllegalArgumentException("maximum must be nonnegative");
        }
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
