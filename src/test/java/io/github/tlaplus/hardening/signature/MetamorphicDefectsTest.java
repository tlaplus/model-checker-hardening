package io.github.tlaplus.hardening.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

/** Checks {@code signatures/metamorphic-defects.toml}, which a metamorphic corpus lists next to all-defects.toml. */
class MetamorphicDefectsTest {
    private static final List<Path> BOTH =
            List.of(KnownDefectDatabase.ALL, KnownDefectDatabase.METAMORPHIC);

    private final SignatureShapes shapes = new SignatureShapes();

    @Test
    void everyReferenceNamesADocumentInTheRepository() throws Exception {
        SignatureShapes.assertReferencesExist(
                KnownDefectDatabase.METAMORPHIC, KnownDefectDatabase.load(List.of(KnownDefectDatabase.METAMORPHIC)));
    }

    /** Loading fails on an id that both databases define, so a corpus can list them together. */
    @Test
    void loadsNextToTheRecallFirstDatabase() throws Exception {
        var database = KnownDefectDatabase.load(BOTH);

        assertTrue(database.signatures().stream().anyMatch(signature -> signature.id().equals("metamorphic-choose")));
    }

    /**
     * Any bounded CHOOSE matches, since whether it has several witnesses is a run-time fact; a
     * bounded quantifier over the same set and predicate does not.
     */
    @Test
    void matchesEveryChooseAndNotAQuantifier() throws Exception {
        var database = KnownDefectDatabase.load(List.of(KnownDefectDatabase.METAMORPHIC));
        var builder = shapes.builder;
        var x = builder.name("x", TlaTypes.INT);
        var set = builder.enumSet(builder.integer(1), builder.integer(2));
        var predicate = builder.lt(x, builder.integer(3));

        assertEquals(List.of("metamorphic-choose"), shapes.ids(database, builder.eql(
                builder.choose(x, set, predicate), builder.integer(1))));
        assertEquals(List.of(), shapes.ids(database, builder.exists(x, set, predicate)));
    }
}
