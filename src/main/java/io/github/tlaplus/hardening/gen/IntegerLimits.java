package io.github.tlaplus.hardening.gen;

import io.github.tlaplus.hardening.common.Preconditions;
import java.util.Objects;

/**
 * Integer literals and the closed integer terminal.
 *
 * <p>ADR 0012. The closed {@code Int} terminal is {@code base}. A small literal decodes one
 * byte into {@code base ± spread}, as a collection size is decoded (ADR 0011); a wide literal a
 * two's-complement payload of up to {@code maximumBytes}. {@code literals} decides the mix.
 *
 * @param maximumBytes maximum two's-complement payload of a wide literal
 * @param base closed terminal and centre of small literals
 * @param spread how far one byte moves a small literal from the base
 * @param literals how literals mix small, boundary and wide values
 */
public record IntegerLimits(int maximumBytes, int base, int spread, IntegerLiteralMode literals) {
    public static final int DEFAULT_MAXIMUM_BYTES = 16;
    public static final int DEFAULT_BASE = 1;
    public static final int DEFAULT_SPREAD = 4;

    public IntegerLimits {
        Preconditions.requireNonnegative(maximumBytes, "maximumIntegerBytes");
        Preconditions.require(spread >= 0 && spread <= CollectionLimits.MAXIMUM_SIZE_SPREAD,
                "integer_literal_spread must be in the range 0.." + CollectionLimits.MAXIMUM_SIZE_SPREAD);
        Objects.requireNonNull(literals, "literals");
    }

    public static IntegerLimits defaults() {
        return new IntegerLimits(DEFAULT_MAXIMUM_BYTES, DEFAULT_BASE, DEFAULT_SPREAD, IntegerLiteralMode.BOUNDARY);
    }
}
