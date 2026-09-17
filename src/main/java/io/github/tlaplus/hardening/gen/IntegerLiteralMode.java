package io.github.tlaplus.hardening.gen;

/**
 * How an integer literal decodes its value (ADR 0012).
 *
 * <p>Markers are Booleans read before the value. {@link #SMALL} reads one: even is a small
 * literal, odd a wide payload. {@link #BOUNDARY} reads a second after an odd first: even is a
 * boundary value, odd a wide payload. Exhausted input therefore always decodes a small literal
 * outside {@link #WIDE}.
 */
public enum IntegerLiteralMode {
    /** Every literal is a two's-complement payload; exhausted input decodes 0. */
    WIDE("wide"),
    /** Half small ({@code integer_base ± integer_literal_spread}), half wide. */
    SMALL("small"),
    /** Half small, a quarter from {@code BoundaryInteger}, a quarter wide. */
    BOUNDARY("boundary");

    private final String configName;

    IntegerLiteralMode(String configName) {
        this.configName = configName;
    }

    /** Returns the name used in the {@code integer_literals} configuration key. */
    public String configName() {
        return configName;
    }
}
