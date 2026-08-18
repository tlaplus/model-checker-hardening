package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class HumanDurationTest {
    @Test
    void formatsWholeHumanTimeParts() {
        assertEquals("0s", HumanDuration.format(Duration.ofMillis(999)));
        assertEquals(
                "2d 3h 4m 5s",
                HumanDuration.format(Duration.ofDays(2)
                        .plusHours(3)
                        .plusMinutes(4)
                        .plusSeconds(5)));
        assertThrows(
                IllegalArgumentException.class,
                () -> HumanDuration.format(Duration.ofSeconds(-1)));
    }
}
