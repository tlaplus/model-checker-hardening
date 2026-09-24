package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusRecord;
import io.github.tlaplus.hardening.corpus.Technique;
import io.github.tlaplus.hardening.gen.InputKind;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayRecordTest {
    private static final ReplayRecord RECORD = new ReplayRecord(
            CorpusRecord.TECHNIQUE, "old", (saved, expected) -> saved + " -> " + expected);

    @TempDir Path temporary;

    @Test
    void acceptsTheUnrecordedValueWithoutWritingIt() throws Exception {
        var corpus = CorpusDirectory.initialize(temporary.resolve("corpus"), "");
        RECORD.verify(corpus, "old", true);
        assertTrue(corpus.readRecord(CorpusRecord.TECHNIQUE).isEmpty());
        assertEquals("old", RECORD.read(corpus));
    }

    @Test
    void recordsANewValueOnlyInAnEmptyCorpusAndThenRequiresIt() throws Exception {
        var corpus = CorpusDirectory.initialize(temporary.resolve("corpus"), "");
        var refused = assertThrows(WorkflowException.class, () -> RECORD.verify(corpus, "new", false));
        assertEquals("old -> new", refused.getMessage());
        try (var lock = corpus.acquireExclusiveLock()) {
            RECORD.verify(corpus, "new", true);
        }
        assertEquals("new", RECORD.read(corpus));
        RECORD.verify(corpus, "new", false);
        var changed = assertThrows(WorkflowException.class, () -> RECORD.verify(corpus, "old", true));
        assertEquals("new -> old", changed.getMessage());
    }

    @Test
    void refusesToRecordANewValueInACorpusWithEntries() throws Exception {
        var corpus = CorpusDirectory.initialize(temporary.resolve("corpus"), "");
        corpus.store(InputKind.EXPRESSION, new byte[] {1, 2, 3});
        assertThrows(WorkflowException.class, () -> RECORD.verify(corpus, "new", true));
    }

    @Test
    void readsACorpusWithoutATechniqueAsPbt() throws Exception {
        var corpus = CorpusDirectory.initialize(temporary.resolve("corpus"), "");
        assertEquals(Technique.PBT, CorpusTechnique.read(corpus));
        CorpusTechnique.verify(corpus, Technique.PBT, false);
    }
}
