package io.github.tlaplus.hardening.architecture.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.common.Role;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoleLabelsTest {
    @Test
    void rolesKeepTheirDocumentedOrder() {
        assertEquals(List.of("ENTRY_POINT", "FACADE", "CONTRACT", "PROCESS", "ADAPTER", "STORE", "CODEC",
                        "POLICY", "MODEL", "CONFIG", "VIEW", "UTILITY"),
                Arrays.stream(Role.values()).map(Enum::name).toList());
    }

    @Test
    void labelsRolesInLowerCase() {
        assertEquals("entry point", RoleLabels.of(Role.ENTRY_POINT));
        assertEquals(RoleLabels.UNTAGGED, RoleLabels.of(null));
    }
}
