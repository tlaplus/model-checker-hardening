package io.github.tlaplus.hardening.signature;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** Checks the database the repository ships, {@code signatures/known-defects.toml}. */
class ShippedKnownDefectsTest {
    private static final Path DATABASE = KnownDefectDatabase.SHIPPED;

    private final SignatureShapes shapes = new SignatureShapes();

    @Test
    void everyReferenceNamesADocumentInTheRepository() throws Exception {
        SignatureShapes.assertReferencesExist(DATABASE, KnownDefectDatabase.load(List.of(DATABASE)));
    }

    /** Each signature matches the shape its document records, and not the nearest legal one. */
    @Test
    void everySignatureMatchesItsShapeAndNotItsNeighbour() throws Exception {
        var database = KnownDefectDatabase.load(List.of(DATABASE));
        var cases = shapes.shippedCases();

        assertEquals(
                cases.keySet(),
                database.signatures().stream().map(KnownDefect::id).collect(Collectors.toSet()));
        for (var signature : database.signatures()) {
            var pair = cases.get(signature.id());
            assertEquals(List.of(signature.id()), shapes.ids(database, pair.get(0)), signature.id());
            assertEquals(List.of(), shapes.ids(database, pair.get(1)), signature.id());
        }
    }
}
