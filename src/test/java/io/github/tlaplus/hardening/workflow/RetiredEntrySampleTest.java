package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusEnvelope;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.CorpusVerdict;
import io.github.tlaplus.hardening.corpus.StageMetadata;
import io.github.tlaplus.hardening.gen.InputKind;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RetiredEntrySampleTest {
    private static final int ENTRIES = 20_000;

    @Test
    void alwaysDecodesALiveEntry() {
        var sample = new RetiredEntrySample(0.0, 1);
        assertTrue(IntStream.range(0, 100).allMatch(index -> sample.decodes(envelope(index, CorpusVerdict.PASS))));
    }

    @Test
    void decodesNoRetiredEntryAtZeroAndEveryOneAtOne() {
        assertEquals(0, selected(new RetiredEntrySample(0.0, 1)));
        assertEquals(ENTRIES, selected(new RetiredEntrySample(1.0, 1)));
        assertEquals(ENTRIES, selected(RetiredEntrySample.ALL));
    }

    @Test
    void decodesAboutTheRequestedShareOfRetiredEntries() {
        // Binomial(20000, 0.01) has mean 200 and standard deviation about 14.
        var count = selected(new RetiredEntrySample(0.01, 7));
        assertTrue(count > 130 && count < 270, "selected " + count);
    }

    @Test
    void selectsByDigestAndSalt() {
        var sample = new RetiredEntrySample(0.5, 7);
        var first = selection(sample);
        assertEquals(first, selection(sample), "the selection depends only on the digest and the salt");
        assertNotEquals(first, selection(new RetiredEntrySample(0.5, 8)), "another salt selects other entries");
    }

    @Test
    void rejectsAFractionOutsideTheUnitInterval() {
        assertThrows(IllegalArgumentException.class, () -> new RetiredEntrySample(-0.01, 0));
        assertThrows(IllegalArgumentException.class, () -> new RetiredEntrySample(1.01, 0));
        assertThrows(IllegalArgumentException.class, () -> new RetiredEntrySample(Double.NaN, 0));
        assertEquals(0.01, RetiredEntrySample.withFreshSalt(RetiredEntrySample.DEFAULT_FRACTION).fraction());
    }

    private static long selected(RetiredEntrySample sample) {
        return IntStream.range(0, ENTRIES).filter(index -> sample.decodes(envelope(index, CorpusVerdict.FAIL))).count();
    }

    private static List<Boolean> selection(RetiredEntrySample sample) {
        return IntStream.range(0, 1_000).mapToObj(index -> sample.decodes(envelope(index, CorpusVerdict.FAIL))).toList();
    }

    /** An entry the quality gate kept ({@code PASS}) or dropped ({@code FAIL}). */
    private static CorpusEnvelope envelope(int index, CorpusVerdict quality) {
        var time = Instant.ofEpochSecond(1);
        return new CorpusEnvelope(
                new CorpusInput(InputKind.EXPRESSION, ByteBuffer.allocate(Integer.BYTES).putInt(index).array()),
                Optional.empty(),
                List.of(new StageMetadata("quality", quality, time, time)));
    }
}
