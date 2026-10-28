package io.github.tlaplus.hardening.workflow;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusRecord;
import io.github.tlaplus.hardening.corpus.Technique;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayRecordTest {
    @TempDir Path temporary;

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
