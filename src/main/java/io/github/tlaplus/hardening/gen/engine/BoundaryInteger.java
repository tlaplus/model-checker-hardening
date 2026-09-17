package io.github.tlaplus.hardening.gen.engine;

import java.math.BigInteger;

/**
 * Integer values at which checker arithmetic changes behaviour (ADR 0012).
 *
 * <p>One input byte indexes this list modulo its size, so declaration order is part of the byte
 * encoding; {@code BoundaryIntegerTest} pins it. The last three lie outside TLC's signed 32-bit
 * range, a known capability difference that the {@code integer-outside-tlc-range} signature
 * quarantines when the literal occurs as such.
 */
enum BoundaryInteger {
    MINUS_ONE(-1),
    ZERO(0),
    ONE(1),
    TWO(2),
    FIFTEEN(15),
    SIXTEEN(16),
    BYTE_MAX(255),
    BYTE_RANGE(256),
    SHORT_MAX(32_767),
    SHORT_RANGE(32_768),
    CHAR_RANGE(65_536),
    SQUARE_ROOT_FITS(46_340),
    SQUARE_ROOT_OVERFLOWS(46_341),
    HALF_INT_RANGE(1 << 30),
    INT_MAX(Integer.MAX_VALUE),
    INT_MIN(Integer.MIN_VALUE),
    INT_MAX_PLUS_ONE(BigInteger.ONE.shiftLeft(31)),
    INT_MIN_MINUS_ONE(BigInteger.ONE.shiftLeft(31).negate().subtract(BigInteger.ONE)),
    LONG_MAX(BigInteger.valueOf(Long.MAX_VALUE));

    private final BigInteger value;

    BoundaryInteger(long value) {
        this(BigInteger.valueOf(value));
    }

    BoundaryInteger(BigInteger value) {
        this.value = value;
    }

    BigInteger value() {
        return value;
    }
}
