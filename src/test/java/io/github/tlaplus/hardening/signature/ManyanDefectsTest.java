package io.github.tlaplus.hardening.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;

import at.forsyte.apalache.tla.lir.TlaEx;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks {@code signatures/manyan-defects.toml}, which a corpus that runs Manyan as its generic
 * checker lists next to all-defects.toml (ADR 0033).
 */
class ManyanDefectsTest {
    private static final String LITERAL_SUBSCRIPT = "manyan-literal-subscript";

    private final SignatureShapes shapes = new SignatureShapes();

    @Test
    void everyReferenceNamesADocumentThatDecidesTheStatus() throws Exception {
        SignatureShapes.assertReferences(KnownDefectDatabase.MANYAN,
                KnownDefectDatabase.load(List.of(KnownDefectDatabase.MANYAN)));
    }

    /** Loading fails on an id that two databases define, so a corpus can list both. */
    @Test
    void loadsNextToTheRecallFirstDatabase() throws Exception {
        KnownDefectDatabase.load(List.of(KnownDefectDatabase.ALL, KnownDefectDatabase.MANYAN));
    }

    /** Every subscripting operator matches with every keyword subscript Manyan rejects. */
    @Test
    void matchesKeywordSubscripts() throws Exception {
        var database = KnownDefectDatabase.load(List.of(KnownDefectDatabase.MANYAN));
        var builder = shapes.builder;

        for (var subscript : keywordSubscripts()) {
            var expected = List.of(LITERAL_SUBSCRIPT);
            assertEquals(expected, shapes.ids(database, builder.stutter(shapes.action(), subscript)));
            assertEquals(expected, shapes.ids(database, builder.noStutter(shapes.action(), subscript)));
            assertEquals(expected, shapes.ids(database, builder.weakFair(subscript, shapes.action())));
            assertEquals(expected, shapes.ids(database, builder.strongFair(subscript, shapes.action())));
        }
    }

    /** A name, the subscript of every generated specification, matches nothing. */
    @Test
    void doesNotMatchANameSubscript() throws Exception {
        var database = KnownDefectDatabase.load(List.of(KnownDefectDatabase.MANYAN));
        var builder = shapes.builder;

        assertEquals(List.of(), shapes.ids(database, builder.always(builder.stutter(shapes.action(), shapes.flag()))));
        assertEquals(List.of(), shapes.ids(database, builder.weakFair(shapes.flag(), shapes.action())));
    }

    private List<TlaEx> keywordSubscripts() {
        var builder = shapes.builder;
        return List.of(builder.bool(false), builder.bool(true), builder.stringSet(), builder.booleanSet());
    }
}
