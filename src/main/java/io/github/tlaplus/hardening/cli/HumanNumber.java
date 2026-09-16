package io.github.tlaplus.hardening.cli;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;

/** Locale-independent numeric fields; compact counts occupy at most five columns. */
final class HumanNumber {
    private static final String[] SUFFIXES = {"", "k", "M", "G", "T", "P", "E"};
    private static final MathContext THREE_DIGITS = new MathContext(3, RoundingMode.HALF_UP);

    private HumanNumber() {}

    static String compact(long value) {
        if (value < 10_000) {
            return Long.toString(value);
        }
        var number = BigDecimal.valueOf(value).round(THREE_DIGITS);
        var unit = 0;
        while (number.compareTo(BigDecimal.valueOf(1000)) >= 0 && unit < SUFFIXES.length - 1) {
            number = number.movePointLeft(3);
            unit++;
        }
        return number.stripTrailingZeros().toPlainString() + SUFFIXES[unit];
    }

    static String richness(double value, Precision precision) {
        var compact = precision == Precision.COMPACT;
        if (value == 0.0) {
            return "0";
        }
        if (value < 0.001 || value >= (compact ? 10_000 : 1_000_000_000_000.0)) {
            return String.format(Locale.ROOT, compact ? "%.1e" : "%.3e", value);
        }
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
