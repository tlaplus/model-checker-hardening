package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationTargetsTest {
    @Test
    void generationZeroIsPbtAndLaterGenerationsReserveTheConfiguredMutantShare() {
        assertEquals(0, GenerationTargets.mutantShare(80, 0, 0.5));
        assertEquals(40, GenerationTargets.mutantShare(80, 1, 0.5));
        assertEquals(0, GenerationTargets.mutantShare(80, 1, 0.0));
        assertEquals(80, GenerationTargets.mutantShare(80, 1, 1.0));
    }

    @Test
    void roundsTheMutantShareOfAPartialFinalGeneration() {
        assertEquals(2, GenerationTargets.mutantShare(3, 1, 0.5));
        assertEquals(1, GenerationTargets.mutantShare(2, 1, 0.5));
    }

    @Test
    void aFreshGenerationSplitsItsRangeIntoAMutantPrefixAndAPbtSuffix() {
        var targets = GenerationTargets.resuming(10, 4, 0, 0);

        assertEquals(10, targets.remaining());
        assertEquals(4, targets.prefixRemaining());
        assertEquals(6, targets.suffixRemaining());
        assertEquals(0, targets.reservePrefix(4));
        assertEquals(4, targets.reserveSuffix(6));
        assertEquals(0, targets.remaining());
    }

    @Test
    void aResumedGenerationReservesOnlyWhatItStillLacks() {
        // 6 of 10 entries were admitted before the interruption, 4 of them mutants.
        var targets = GenerationTargets.resuming(10, 4, 6, 4);

        assertEquals(4, targets.remaining());
        assertEquals(0, targets.prefixRemaining());
        assertEquals(4, targets.suffixRemaining());
        assertEquals(6, targets.reserveSuffix(4));
    }

    @Test
    void pbtThatSpilledIntoThePrefixDoesNotFreeItsOrdinalsForMutants() {
        // The interrupted run found no parents, so PBT filled the whole suffix and two prefix slots.
        var targets = GenerationTargets.resuming(10, 4, 8, 0);

        assertEquals(2, targets.remaining());
        assertEquals(2, targets.prefixRemaining());
        assertEquals(0, targets.suffixRemaining());
        // The mutants now admitted must not replay the streams the spilled PBT entries already used.
        assertEquals(2, targets.reservePrefix(2));
    }

    @Test
    void aGenerationHoldingMoreMutantsThanTheRatioNowCallsForKeepsThemInItsPrefix() {
        // feedback_ratio was lowered between runs: the share is 2, but 6 mutants exist.
        var targets = GenerationTargets.resuming(10, 2, 6, 6);

        assertEquals(4, targets.remaining());
        assertEquals(0, targets.prefixRemaining());
        assertEquals(4, targets.suffixRemaining());
        // The suffix starts past every mutant, not at the lowered share.
        assertEquals(6, targets.reserveSuffix(4));
    }

    @Test
    void aGenerationAlreadyOverItsSizeReservesNothing() {
        // generation_size was lowered between runs.
        var targets = GenerationTargets.resuming(4, 2, 10, 5);

        assertEquals(0, targets.prefixRemaining());
        assertEquals(0, targets.suffixRemaining());
    }

    @Test
    void everyReservationOfAGenerationIsDisjointAndWithinItsRange() {
        for (var admitted = 0; admitted <= 10; admitted++) {
            for (var mutants = 0; mutants <= admitted; mutants++) {
                for (var share = 0; share <= 10; share++) {
                    assertDisjoint(10, share, admitted, mutants);
                }
            }
        }
    }

    /**
     * Reserves everything a generation still lacks, in the order {@link GenerationLoop} does, and
     * requires that no two targets share an ordinal. A shared ordinal would replay one candidate
     * stream for two entries.
     */
    private static void assertDisjoint(long size, long share, long admitted, long mutants) {
        var targets = GenerationTargets.resuming(size, share, admitted, mutants);
        var reserved = new ArrayList<Long>();
        var prefix = targets.prefixRemaining();
        if (prefix > 0) {
            addRange(reserved, targets.reservePrefix(prefix), prefix);
        }
        var suffix = targets.suffixRemaining();
        if (suffix > 0) {
            addRange(reserved, targets.reserveSuffix(suffix), suffix);
        }

        var context = "size=" + size + " share=" + share
                + " admitted=" + admitted + " mutants=" + mutants;
        assertEquals(reserved.size(), new HashSet<>(reserved).size(), "overlapping targets: " + context);
        assertEquals(Math.max(0, size - Math.min(size, admitted)), reserved.size(),
                "wrong number of targets: " + context);
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
