package io.github.tlaplus.hardening.workflow;

import static io.github.tlaplus.hardening.corpus.EntryOrigin.ADOPTED;
import static io.github.tlaplus.hardening.corpus.EntryOrigin.MUTANT;
import static io.github.tlaplus.hardening.corpus.EntryOrigin.PBT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.EntryOrigin;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenerationTargetsTest {
    @Test
    void generationZeroHasNoMutantsAndLaterGenerationsReserveTheConfiguredShare() {
        assertEquals(0, GenerationTargets.mutantShare(80, 0, 0.5));
        assertEquals(40, GenerationTargets.mutantShare(80, 1, 0.5));
        assertEquals(0, GenerationTargets.mutantShare(80, 1, 0.0));
        assertEquals(80, GenerationTargets.mutantShare(80, 1, 1.0));
        assertEquals(20, GenerationTargets.adoptShare(80, 0.25));
    }

    @Test
    void roundsTheMutantShareOfAPartialFinalGeneration() {
        assertEquals(2, GenerationTargets.mutantShare(3, 1, 0.5));
        assertEquals(1, GenerationTargets.mutantShare(2, 1, 0.5));
    }

    @Test
    void aFreshGenerationSplitsItsRangeIntoMutantAdoptedAndPbtRanges() {
        var targets = GenerationTargets.resuming(10, Map.of(MUTANT, 4L, ADOPTED, 3L), Map.of());

        assertEquals(10, targets.remaining());
        assertEquals(4, targets.remaining(MUTANT));
        assertEquals(3, targets.remaining(ADOPTED));
        assertEquals(3, targets.remaining(PBT));
        assertEquals(0, targets.reserve(MUTANT, 4));
        assertEquals(4, targets.reserve(ADOPTED, 3));
        assertEquals(7, targets.reserve(PBT, 3));
        assertEquals(0, targets.remaining());
    }

    @Test
    void aResumedGenerationReservesOnlyWhatItStillLacks() {
        // 6 of 10 entries were admitted before the interruption, 4 of them mutants.
        var targets = GenerationTargets.resuming(10, Map.of(MUTANT, 4L), Map.of(MUTANT, 4L, PBT, 2L));

        assertEquals(4, targets.remaining());
        assertEquals(0, targets.remaining(MUTANT));
        assertEquals(4, targets.remaining(PBT));
        assertEquals(6, targets.reserve(PBT, 4));
    }

    @Test
    void pbtThatSpilledIntoAnEarlierRangeDoesNotFreeItsOrdinals() {
        // The interrupted run found no parents, so PBT filled its own range and two mutant slots.
        var targets = GenerationTargets.resuming(10, Map.of(MUTANT, 4L), Map.of(PBT, 8L));

        assertEquals(2, targets.remaining());
        assertEquals(2, targets.remaining(MUTANT));
        assertEquals(0, targets.remaining(PBT));
        // The mutants now admitted must not replay the streams the spilled PBT entries already used.
        assertEquals(2, targets.reserve(MUTANT, 2));
    }

    @Test
    void spilledPbtFillsTheLatestEarlierRangeFirst() {
        var targets = GenerationTargets.resuming(10, Map.of(MUTANT, 3L, ADOPTED, 3L), Map.of(PBT, 6L));

        // Two PBT entries spilled past the PBT range [6, 10); they took adopted ordinals 3 and 4.
        assertEquals(1, targets.remaining(ADOPTED));
        assertEquals(3, targets.remaining(MUTANT));
        assertEquals(5, targets.reserve(ADOPTED, 1));
    }

    @Test
    void aGenerationHoldingMoreMutantsThanTheRatioNowCallsForKeepsThemInTheirRange() {
        // feedback_ratio was lowered between runs: the share is 2, but 6 mutants exist.
        var targets = GenerationTargets.resuming(10, Map.of(MUTANT, 2L), Map.of(MUTANT, 6L));

        assertEquals(4, targets.remaining());
        assertEquals(0, targets.remaining(MUTANT));
        assertEquals(4, targets.remaining(PBT));
        // The PBT range starts past every mutant, not at the lowered share.
        assertEquals(6, targets.reserve(PBT, 4));
    }

    @Test
    void aGenerationAlreadyOverItsSizeReservesNothing() {
        // generation_size was lowered between runs.
        var targets = GenerationTargets.resuming(4, Map.of(MUTANT, 2L), Map.of(MUTANT, 5L, PBT, 5L));

        assertEquals(0, targets.remaining());
    }

    @Test
    void everyReservationOfAGenerationIsDisjointAndWithinItsRange() {
        for (var mutants = 0L; mutants <= 6; mutants++) {
            for (var adopted = 0L; adopted <= 6 - mutants; adopted++) {
                for (var pbt = 0L; pbt <= 10 - mutants - adopted; pbt++) {
                    for (var mutantShare = 0L; mutantShare <= 10; mutantShare += 2) {
                        for (var adoptShare = 0L; adoptShare <= 10 - mutantShare; adoptShare += 3) {
                            assertDisjoint(10, Map.of(MUTANT, mutantShare, ADOPTED, adoptShare),
                                    Map.of(MUTANT, mutants, ADOPTED, adopted, PBT, pbt));
                        }
                    }
                }
            }
        }
    }

    /**
     * Reserves everything a generation still lacks, in the order {@link GenerationLoop} does, and
     * requires that no two targets share an ordinal. A shared ordinal would replay one candidate
     * stream for two entries. The held entries occupy the ordinals below their ranges' reservations.
     */
    private static void assertDisjoint(long size, Map<EntryOrigin, Long> shares, Map<EntryOrigin, Long> held) {
        var targets = GenerationTargets.resuming(size, shares, held);
        var reserved = new ArrayList<Long>();
        for (var origin : EntryOrigin.values()) {
            var count = targets.remaining(origin);
            if (count > 0) {
                addRange(reserved, targets.reserve(origin, count), count);
            }
        }
        var admitted = held.values().stream().mapToLong(Long::longValue).sum();
        var context = "shares=" + shares + " held=" + held;
        assertEquals(reserved.size(), new HashSet<>(reserved).size(), "overlapping targets: " + context);
        assertEquals(Math.max(0, size - Math.min(size, admitted)), reserved.size(), "wrong number of targets: " + context);
        for (var target : reserved) {
            assertTrue(target >= 0 && target < size, "target out of range: " + target + " " + context);
        }
    }

    private static void addRange(List<Long> reserved, long first, long count) {
        for (var offset = 0L; offset < count; offset++) {
            reserved.add(first + offset);
        }
    }
}
