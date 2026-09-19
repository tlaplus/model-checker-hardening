package io.github.tlaplus.hardening.workflow.quality;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.common.ExprCounts;
import io.github.tlaplus.hardening.common.ExprEdge;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class CoverageFeatureTest {
    @Test
    void bucketsHitCountsAsAfl() {
        var expected = new TreeMap<Long, Integer>(Map.ofEntries(
                Map.entry(0L, 0), Map.entry(1L, 1), Map.entry(2L, 2), Map.entry(3L, 3),
                Map.entry(4L, 4), Map.entry(7L, 4), Map.entry(8L, 5), Map.entry(15L, 5),
                Map.entry(16L, 6), Map.entry(31L, 6), Map.entry(32L, 7), Map.entry(127L, 7),
                Map.entry(128L, 8), Map.entry(Long.MAX_VALUE, 8)));
        expected.forEach((occurrences, bucket) ->
                assertEquals(bucket, CoverageFeature.bucket(occurrences), "occurrences " + occurrences));
    }

    @Test
    void hasOneFeaturePerConstructAndPerEdge() {
        var counts = new ExprCounts(
                7,
                new TreeMap<>(Map.of("EQ", 1L, "TlaInt", 5L)),
                new TreeMap<>(Map.of(new ExprEdge("EQ", "TlaInt"), 2L)));

        assertEquals(
                Set.of(
                        new CoverageFeature.Construct("EQ", 1),
                        new CoverageFeature.Construct("TlaInt", 4),
                        new CoverageFeature.Edge(new ExprEdge("EQ", "TlaInt"), 2)),
                CoverageFeature.of(counts));
    }
}
