package io.github.tlaplus.hardening.workflow.input;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.tlaplus.hardening.corpus.EntryOrigin;
import io.github.tlaplus.hardening.mutation.MutationOperator;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ReplayCandidatesTest {
    private static final String PARENT = "ab".repeat(32);

    /** A replay target has one candidate, its parent's cohort, and replay provenance (ADR 0034 §6). */
    @Test
    void offersTheSamePayloadOnceWithReplayProvenance() {
        var payload = new byte[] {1, 2, 3};
        var target = new ReplayCandidates(PARENT, 4, payload).claim(new SplittableRandom(0));

        var draft = target.draw(new SplittableRandom(1));

        assertFalse(target.retries());
        assertEquals(0.0, target.richnessThreshold());
        assertArrayEquals(payload, draft.input());
        assertArrayEquals(payload, target.draw(new SplittableRandom(2)).input());
        assertEquals(4, draft.cohort());
        assertEquals(PARENT, draft.mutation().orElseThrow().parent());
        assertEquals(List.of(MutationOperator.REPLAY), draft.mutation().orElseThrow().operators());
        assertEquals(EntryOrigin.REPLAY, EntryOrigin.of(draft.mutation()));
    }
}
