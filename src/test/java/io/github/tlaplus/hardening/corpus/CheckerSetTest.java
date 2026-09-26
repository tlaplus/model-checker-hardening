package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class CheckerSetTest {
    @Test
    void ordersCheckersByStage() {
        assertEquals(
                List.of(CorpusStage.TLC, CorpusStage.APALACHE),
                CheckerSet.of(CorpusStage.APALACHE, CorpusStage.TLC).stages());
        assertEquals(CorpusStage.checkerBranches(), CheckerSet.ALL.stages());
        assertEquals(CorpusStage.TLC, CheckerSet.of(CorpusStage.TLC).first());
    }

    @Test
    void rejectsAnEmptyDuplicateOrNonCheckerSet() {
        assertThrows(IllegalArgumentException.class, () -> new CheckerSet(List.of()));
        assertThrows(IllegalArgumentException.class, () -> CheckerSet.of(CorpusStage.TLC, CorpusStage.TLC));
        assertThrows(IllegalArgumentException.class, () -> CheckerSet.of(CorpusStage.PARSER));
    }
}
