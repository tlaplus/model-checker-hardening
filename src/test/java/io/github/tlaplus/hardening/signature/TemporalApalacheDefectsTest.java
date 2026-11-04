package io.github.tlaplus.hardening.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks {@code signatures/temporal-apalache-defects.toml}, which a temporal metamorphic corpus
 * that checks with Apalache lists next to all-defects.toml and metamorphic-defects.toml (ADR 0029
 * §3).
 */
class TemporalApalacheDefectsTest {
    private static final List<Path> ALL_THREE = List.of(
            KnownDefectDatabase.ALL, KnownDefectDatabase.METAMORPHIC, KnownDefectDatabase.TEMPORAL_APALACHE);

    private final SignatureShapes shapes = new SignatureShapes();

    @Test
    void everyReferenceNamesADocumentThatDecidesTheStatus() throws Exception {
        SignatureShapes.assertReferences(KnownDefectDatabase.TEMPORAL_APALACHE,
                KnownDefectDatabase.load(List.of(KnownDefectDatabase.TEMPORAL_APALACHE)));
    }

    /** Loading fails on an id that two databases define, so a corpus can list the three together. */
    @Test
    void loadsNextToTheOtherDatabases() throws Exception {
        KnownDefectDatabase.load(ALL_THREE);
    }

    /** ENABLED, WF and SF match; the temporal operators Apalache supports do not. */
    @Test
    void matchesWhatApalacheDoesNotSupportAndNothingElse() throws Exception {
        var database = KnownDefectDatabase.load(List.of(KnownDefectDatabase.TEMPORAL_APALACHE));
        var builder = shapes.builder;

        assertEquals(List.of("temporal-apalache-enabled"), shapes.ids(database, builder.enabled(shapes.action())));
        assertEquals(List.of("temporal-apalache-fairness"),
                shapes.ids(database, builder.weakFair(shapes.flag(), shapes.action())));
        assertEquals(List.of("temporal-apalache-fairness"),
                shapes.ids(database, builder.strongFair(shapes.flag(), shapes.action())));
        assertEquals(List.of(), shapes.ids(database, builder.eventually(shapes.flag())));
        assertEquals(List.of(), shapes.ids(database, builder.always(builder.stutter(shapes.action(), shapes.flag()))));
    }
}
