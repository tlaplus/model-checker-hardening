package io.github.tlaplus.hardening.workflow;

import static java.nio.charset.StandardCharsets.UTF_8;
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
    private static final ReplayRecord<String> RECORD = new ReplayRecord<>(
            CorpusRecord.TECHNIQUE, "old", ReplayRecord.Codec.TEXT, (saved, expected) -> saved + " -> " + expected);

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

    /** A legacy .technique must agree with [workflow] technique; a corpus without one takes the key. */
    @Test
    void aLegacyTechniqueMustMatchTheConfiguredOne() throws Exception {
        var corpus = CorpusDirectory.initialize(temporary.resolve("corpus"), "");
        CorpusRecords.TECHNIQUE.requireUnchanged(corpus, Technique.GBT);
        corpus.writeRecord(CorpusRecord.TECHNIQUE, "gbt".getBytes(UTF_8));
        CorpusRecords.TECHNIQUE.requireUnchanged(corpus, Technique.GBT);
        var refused = assertThrows(WorkflowException.class,
                () -> CorpusRecords.TECHNIQUE.requireUnchanged(corpus, Technique.PBT));
        assertEquals("this corpus holds gbt entries, but its config.toml sets technique = \"pbt\"; set technique ="
                + " \"gbt\" under [workflow]", refused.getMessage());
    }

    @Test
    void readsACorpusWithoutATechniqueAsPbt() throws Exception {
        var corpus = CorpusDirectory.initialize(temporary.resolve("corpus"), "");
        assertEquals(Technique.PBT, CorpusRecords.TECHNIQUE.read(corpus));
        CorpusRecords.TECHNIQUE.requireUnchanged(corpus, Technique.PBT);
    }
}
