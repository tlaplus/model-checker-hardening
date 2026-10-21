package io.github.tlaplus.hardening.architecture.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RelationKindTest {
    @Test
    void relationKindsAreOrderedByStrength() {
        assertEquals(List.of("EXTENDS", "IMPLEMENTS", "HAS", "USES", "DEPENDS"),
                Arrays.stream(RelationKind.values()).map(Enum::name).toList());
        assertEquals(RelationKind.HAS, RelationKind.USES.strongest(RelationKind.HAS));
        assertEquals(RelationKind.HAS, RelationKind.HAS.strongest(RelationKind.DEPENDS));
    }
}
