package io.github.tlaplus.hardening.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnownDefectDatabaseTest {
    private static final String VALID = """
            [[signature]]
            id = "modulo-by-literal-zero"
            references = ["../conformance/modulo-by-zero-apalache-fails.md"]
            description = "Mod by zero."
            match = ['(MOD _ 0)', '(MOD _ (: 0 "Int"))']
            """;

    @Test
    void readsEverySignatureField() throws Exception {
        var signatures = KnownDefectDatabaseReader.parse(VALID, "test.toml");

        assertEquals(1, signatures.size());
        var signature = signatures.getFirst();
        assertEquals("modulo-by-literal-zero", signature.id());
        assertEquals(List.of("../conformance/modulo-by-zero-apalache-fails.md"), signature.references());
        assertEquals("Mod by zero.", signature.description());
        assertEquals(List.of(), KnownDefectDatabaseReader.parse("", "empty.toml"));
    }

    @Test
    void rejectsMalformedDatabasesNamingTheSignatureAndPattern() {
        assertInvalid(
                VALID.replace("description = \"Mod by zero.\"\n", ""),
                "test.toml: signature 1: missing keys: description");
        assertInvalid(VALID + "severity = 1\n", "signature 1: unknown keys: severity");
        assertInvalid("title = \"x\"\n" + VALID, "test.toml: unknown keys: title");
        assertInvalid(
                VALID.replace("modulo-by-literal-zero", "Modulo_Zero"),
                "must consist of lowercase letters, digits, and hyphens");
        assertInvalid(
                VALID.replace("match = ['(MOD _ 0)', '(MOD _ (: 0 \"Int\"))']", "match = []"),
                "'match' must not be empty");
        assertInvalid(
                VALID.replace("'(MOD _ 0)'", "'(MODULO _ 0)'"),
                "signature 1 ('modulo-by-literal-zero'): match[0]: column 2: unknown operator 'MODULO'");
        assertInvalid(
                VALID.replace("[\"../conformance/modulo-by-zero-apalache-fails.md\"]", "\"x\""),
                "expected 'references' to be an array");
        assertInvalid("signature = 1\n", "expected [[signature]] tables");
        assertInvalid("[[signature\n", "invalid TOML");
    }

    @Test
    void rejectsAnIdThatTwoDatabasesDefine(@TempDir Path directory) throws Exception {
        var first = Files.writeString(directory.resolve("first.toml"), VALID);
        var second = Files.writeString(directory.resolve("second.toml"), VALID);

        var failure = assertThrows(
                KnownDefectDatabaseException.class,
                () -> KnownDefectDatabase.load(List.of(first, second)));

        assertTrue(failure.getMessage().contains(
                "signature 'modulo-by-literal-zero' is already defined in " + first), failure.getMessage());
    }

    @Test
    void reportsAnUnreadableDatabase(@TempDir Path directory) {
        var failure = assertThrows(
                KnownDefectDatabaseException.class,
                () -> KnownDefectDatabase.load(List.of(directory.resolve("missing.toml"))));

        assertTrue(failure.getMessage().contains("cannot read known-defect database"));
    }

    @Test
    void anEmptyDatabaseMatchesNothing() throws Exception {
        var builder = new TlaTypedScopeUncheckedBuilder();
        var module = TlaModules.create(
                "M", List.of(builder.decl("Op", builder.mod(builder.integer(1), builder.integer(0)))));

        assertTrue(KnownDefectDatabase.load(List.of()).isEmpty());
        assertEquals(List.of(), KnownDefectDatabase.empty().matches(module, List.of("Op")));
    }

    private static void assertInvalid(String source, String message) {
        var failure = assertThrows(
                KnownDefectDatabaseException.class,
                () -> KnownDefectDatabaseReader.parse(source, "test.toml"));
        assertTrue(failure.getMessage().contains(message), failure.getMessage());
    }
}
