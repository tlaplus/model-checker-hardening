package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TechniqueTest {
    /** The encoded names are stored in `.technique` and accepted by `--how`. */
    @Test
    void pinsEncodedNamesAndOracles() {
        assertEquals(List.of("pbt"), Technique.encodedNames());
        assertEquals(Oracle.CONFORMANCE, Technique.PBT.oracle());
        assertEquals(Technique.PBT, Technique.UNRECORDED);
    }

    @Test
    void decodesEveryEncodedName() {
        for (var technique : Technique.values()) {
            assertEquals(Optional.of(technique), Technique.fromEncodedName(technique.encodedName()));
        }
        assertEquals(Optional.empty(), Technique.fromEncodedName("PBT"));
        assertEquals(Technique.values().length, Arrays.stream(Technique.values()).distinct().count());
    }
}
