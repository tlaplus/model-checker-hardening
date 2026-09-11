package io.github.tlaplus.hardening.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

/** Checks the database the repository ships, {@code signatures/known-defects.toml}. */
class ShippedKnownDefectsTest {
    private static final Path DATABASE = KnownDefectDatabase.SHIPPED;

    private final TlaTypedScopeUncheckedBuilder builder = new TlaTypedScopeUncheckedBuilder();

    @Test
    void everyReferenceNamesADocumentInTheRepository() throws Exception {
        var database = KnownDefectDatabase.load(List.of(DATABASE));

        for (var signature : database.signatures()) {
            for (var reference : signature.references()) {
                assertTrue(
                        Files.isRegularFile(DATABASE.getParent().resolve(reference)),
                        signature.id() + " references a missing document: " + reference);
            }
        }
    }

    /** Each signature matches the shape its document records, and not the nearest legal one. */
    @Test
    void everySignatureMatchesItsShapeAndNotItsNeighbour() throws Exception {
        var database = KnownDefectDatabase.load(List.of(DATABASE));
        var step = builder.name("step", TlaTypes.INT);
        var zero = builder.integer(0);
        var cases = Map.of(
                "modulo-by-literal-zero",
                List.of(builder.mod(step, zero), builder.mod(step, builder.integer(2))),
                "division-by-literal-zero",
                List.of(builder.div(step, zero), builder.div(step, builder.integer(2))),
                "zero-power-zero",
                List.of(builder.exp(zero, builder.integer(0)), builder.exp(step, zero)),
                "sequence-set",
                List.of(
                        builder.seqSet(builder.enumSet(builder.integer(1))),
                        builder.enumSet(builder.seq(builder.integer(1)))),
                "string-set",
                List.of(builder.stringSet(), builder.enumSet(builder.str("a"))));

        assertEquals(
                cases.keySet(),
                database.signatures().stream().map(KnownDefect::id).collect(java.util.stream.Collectors.toSet()));
        for (var signature : database.signatures()) {
            var shapes = cases.get(signature.id());
            assertEquals(List.of(signature.id()), ids(database, module(shapes.get(0))), signature.id());
            assertEquals(List.of(), ids(database, module(shapes.get(1))), signature.id());
        }
    }

    private TlaModule module(TlaEx expression) {
        return TlaModules.create("KnownDefect", List.of(builder.decl("Expression", expression)));
    }

    private static List<String> ids(KnownDefectDatabase database, TlaModule module) {
        return database.matches(module, List.of("Expression")).stream()
                .map(match -> match.defect().id())
                .toList();
    }
}
