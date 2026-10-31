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
            status = "open"
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
        assertEquals(DefectStatus.OPEN, signature.status());
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
        assertInvalid(
                VALID.replace("status = \"open\"\n", ""), "test.toml: signature 1: missing keys: status");
        assertInvalid(
                VALID.replace("status = \"open\"", "status = \"fixed\""),
                "signature 1 ('modulo-by-literal-zero'): 'status' must be one of open, closed, not 'fixed'");
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

    /** A closed signature still matches, but only an open one keeps the module out of the corpus. */
    @Test
    void onlyOpenSignaturesQuarantine(@TempDir Path directory) throws Exception {
        var database = Files.writeString(directory.resolve("known-defects.toml"), VALID + """

                [[signature]]
                id = "fixed-division-by-literal-zero"
                references = ["none"]
                description = "Div by zero, since fixed."
                status = "closed"
                match = ['(DIV _ 0)']
                """);
        var builder = new TlaTypedScopeUncheckedBuilder();
        var division = TlaModules.create(
                "M", List.of(builder.decl("Op", builder.div(builder.integer(1), builder.integer(0)))));
        var both = TlaModules.create("M", List.of(builder.decl("Op", builder.plus(
                builder.div(builder.integer(1), builder.integer(0)),
                builder.mod(builder.integer(1), builder.integer(0))))));
        var loaded = KnownDefectDatabase.load(List.of(database));

        assertEquals(List.of("fixed-division-by-literal-zero"), ids(loaded.matches(division, List.of("Op"))));
        assertEquals(List.of(), loaded.quarantining(division, List.of("Op")));
        assertEquals(List.of("modulo-by-literal-zero"), ids(loaded.quarantining(both, List.of("Op"))));
    }

    private static List<String> ids(List<KnownDefectMatch> matches) {
        return matches.stream().map(match -> match.defect().id()).toList();
    }

    private static void assertInvalid(String source, String message) {
        var failure = assertThrows(
                KnownDefectDatabaseException.class,
                () -> KnownDefectDatabaseReader.parse(source, "test.toml"));
        assertTrue(failure.getMessage().contains(message), failure.getMessage());
    }
}
