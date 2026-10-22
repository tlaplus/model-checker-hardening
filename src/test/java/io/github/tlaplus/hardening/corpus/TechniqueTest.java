package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.InputKind;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TechniqueTest {
    /** The encoded names are stored in `.technique` and accepted by `--how`. */
    @Test
    void pinsEncodedNamesAndOracles() {
        assertEquals(List.of("pbt", "mt", "gbt"), Technique.encodedNames());
        assertEquals(Oracle.CONFORMANCE, Technique.PBT.oracle());
        assertEquals(Oracle.METAMORPHIC, Technique.MT.oracle());
        assertEquals(Oracle.CONFORMANCE, Technique.GBT.oracle());
        assertEquals(Technique.PBT, Technique.UNRECORDED);
    }

    /** pbt generates every kind, mt rewrites no graph, and gbt generates only graphs (ADR 0023 §1). */
    @Test
    void namesTheKindsEachTechniqueGenerates() {
        for (var kind : InputKind.values()) {
            assertTrue(Technique.PBT.generates(kind), kind.encodedName());
            assertEquals(kind != InputKind.GRAPH, Technique.MT.generates(kind), kind.encodedName());
            assertEquals(kind == InputKind.GRAPH, Technique.GBT.generates(kind), kind.encodedName());
        }
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
