package io.github.tlaplus.hardening.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.signature.KnownDefectDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnownDefectReportTest {
    @Test
    void reportsEachMatchingSignatureWithTheSubexpressionItMatched(@TempDir Path directory)
            throws Exception {
        var database = Files.writeString(directory.resolve("known-defects.toml"), """
                [[signature]]
                id = "modulo-by-literal-zero"
                references = ["../conformance/modulo-by-zero-apalache-fails.md"]
                description = "Mod by zero."
                match = ['(MOD _ 0)']
                """);
        var builder = new TlaTypedScopeUncheckedBuilder();
        var module = TlaModules.create("M", List.of(builder.decl(
                "Inv",
                builder.eql(builder.mod(builder.integer(1), builder.integer(0)), builder.integer(0)))));

        var report = KnownDefectReport.render(
                KnownDefectDatabase.load(List.of(database)).matches(module, List.of("Inv")));

        assertEquals(
                String.join(
                        System.lineSeparator(),
                        "modulo-by-literal-zero: Mod by zero.",
                        "  reference: ../conformance/modulo-by-zero-apalache-fails.md",
                        "  matched:",
                        "    1 % 0",
                        ""),
                report);
    }

    @Test
    void reportsThatNothingMatched() {
        assertEquals(
                KnownDefectReport.NO_MATCH + System.lineSeparator(), KnownDefectReport.render(List.of()));
    }
}
