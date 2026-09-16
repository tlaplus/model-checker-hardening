package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class HumanNumberTest {
    @Test
    void compactCountsHandleRoundingAcrossSuffixBoundariesAndLongMax() {
        assertEquals("9999", HumanNumber.compact(9999));
        assertEquals("10k", HumanNumber.compact(10_000));
        assertEquals("12.3k", HumanNumber.compact(12_345));
        assertEquals("999k", HumanNumber.compact(999_499));
        assertEquals("1M", HumanNumber.compact(999_500));
        assertEquals("9.22E", HumanNumber.compact(Long.MAX_VALUE));
    }

    @Test
    void formattingIsIndependentOfProcessLocale() {
        var before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("12.3k", HumanNumber.compact(12_345));
            assertEquals("4.5", HumanNumber.richness(4.5, Precision.COMPACT));
            assertEquals("1.0e-05", HumanNumber.richness(0.00001, Precision.COMPACT));
        } finally {
            Locale.setDefault(before);
        }
    }
}
