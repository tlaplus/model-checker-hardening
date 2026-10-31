package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Preconditions;
import io.github.tlaplus.hardening.common.Role;
import java.util.SplittableRandom;

/**
 * The {@code [seeds]} table (ADR 0026): every random seed a run uses. {@code fuzztla init} draws
 * them, so two runs of fresh corpora with the same table draw the same candidates.
 *
 * @param generator the base of every generator worker's random stream (ADR 0010)
 * @param tlcFingerprint TLC's {@code -fp}: the index of its fingerprint polynomial, shared by every
 *     TLC stage, so TLC and TLC-n hash states alike
 * @param tlcSeed TLC's {@code -seed}, the seed of the operators that draw random values
 */
@ArchRole(Role.CONFIG)
public record SeedConfig(long generator, int tlcFingerprint, long tlcSeed) {
    /** The number of fingerprint polynomials TLC offers, {@code tlc2.util.FP64.Polys.length}. */
    public static final int TLC_FINGERPRINT_POLYNOMIALS = 131;

    public SeedConfig {
        Preconditions.requireNonnegative(generator, "seeds.generator");
        Preconditions.require(tlcFingerprint >= 0 && tlcFingerprint < TLC_FINGERPRINT_POLYNOMIALS,
                "seeds.tlc_fp must lie in 0.." + (TLC_FINGERPRINT_POLYNOMIALS - 1));
    }

    /** Returns the seeds that follow from {@code generator}, which fixes the TLC seeds as well. */
    public static SeedConfig derive(long generator) {
        Preconditions.requireNonnegative(generator, "seeds.generator");
        var random = new SplittableRandom(generator);
        return new SeedConfig(generator, random.nextInt(TLC_FINGERPRINT_POLYNOMIALS), random.nextLong());
    }
}
