package io.github.tlaplus.hardening.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ArchModuleTest {
    @Test
    void mapsPackagesByTheirFirstSegment() {
        assertEquals(Optional.of(ArchModule.LAUNCHER), ArchModule.ofPackage(ArchModule.ROOT_PACKAGE));
        assertEquals(Optional.of(ArchModule.CORPUS), ofSubpackage("corpus"));
        assertEquals(Optional.of(ArchModule.WORKFLOW), ofSubpackage("workflow.tlc"));
        assertEquals(Optional.of(ArchModule.GEN), ofSubpackage("gen.engine"));
    }

    @Test
    void rejectsPackagesOutsideTheModules() {
        assertEquals(Optional.empty(), ofSubpackage("unknown"));
        assertEquals(Optional.empty(), ArchModule.ofPackage("io.github.tlaplus.hardeningx"));
        assertEquals(Optional.empty(), ArchModule.ofPackage("tlc2.tool"));
    }

    @Test
    void namesPackages() {
        assertEquals("io.github.tlaplus.hardening", ArchModule.LAUNCHER.packageName());
        assertEquals("root", ArchModule.LAUNCHER.displayName());
        assertEquals("io.github.tlaplus.hardening.cli", ArchModule.CLI.packageName());
        assertEquals("cli", ArchModule.CLI.displayName());
    }

    @Test
    void everyModuleAllowsItselfAndCommon() {
        for (var module : ArchModule.values()) {
            assertTrue(module.allows(module));
            assertTrue(module == ArchModule.LAUNCHER || module.allows(ArchModule.COMMON), module::name);
        }
        assertFalse(ArchModule.CORPUS.allows(ArchModule.WORKFLOW));
    }

    /** Declaration order is layering order: each constant lists only earlier constants. */
    @Test
    void declarationOrderIsLayeringOrder() {
        assertEquals(List.of("COMMON", "CHECKER", "GEN", "SIGNATURE", "MUTATION", "CORPUS",
                        "CONFIG", "DATABASE", "WORKFLOW", "CLI", "LAUNCHER"),
                Arrays.stream(ArchModule.values()).map(Enum::name).toList());
        for (var module : ArchModule.values()) {
            for (var target : module.allowed()) {
                assertTrue(target.ordinal() < module.ordinal(), module + " lists " + target);
            }
        }
    }

    private static Optional<ArchModule> ofSubpackage(String suffix) {
        return ArchModule.ofPackage(ArchModule.ROOT_PACKAGE + "." + suffix);
    }
}
