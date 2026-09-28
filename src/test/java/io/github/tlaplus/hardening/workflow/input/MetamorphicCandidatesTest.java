package io.github.tlaplus.hardening.workflow.input;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.common.Digests;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusInputCodec;
import io.github.tlaplus.hardening.corpus.CorpusPath;
import io.github.tlaplus.hardening.corpus.EntryOrigin;
import io.github.tlaplus.hardening.corpus.GenerationMetadata;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.rewrite.MetamorphicPayload;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MetamorphicCandidatesTest {
    @Test
    void anAdoptedCandidateKeepsItsParentsPayloadAsItsBase(@TempDir Path directory) throws Exception {
        var corpus = CorpusDirectory.initialize(directory.resolve("base"), TomlConfig.render(FuzzTlaConfig.defaults()));
        var parent = new byte[] {4, 8, 15, 16, 23, 42};
        Files.write(
                corpus.resolve(CorpusPath.QUALITY_PASS).resolve(Digests.digest(parent) + ".cbor"),
                CorpusInputCodec.encode(new CorpusInput(InputKind.EXPRESSION, parent), GenerationMetadata.generated(0, 3, 1.0)));
        var pool = ParentPool.load(corpus, InputKind.EXPRESSION);
        assertEquals(1, pool.size());

        var draft = new MetamorphicCandidates(pool).claim(new SplittableRandom(1)).draw(new SplittableRandom(2));

        var parts = MetamorphicPayload.split(new Draw(draft.input()));
        assertArrayEquals(parent, parts.base().drawBytes(parts.base().remaining()));
        assertTrue(parts.rewrite().remaining() > 0);
        assertEquals(EntryOrigin.ADOPTED, EntryOrigin.of(draft.mutation()));
        assertEquals(3, draft.cohort());
    }
}
