package io.github.tlaplus.hardening.workflow.input;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.Technique;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.rewrite.MetamorphicPayload;
import java.util.Arrays;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class CandidateLayoutTest {
    @Test
    void eachTechniqueReadsItsLayout() {
        assertSame(CandidateLayout.CONFORMANCE, CandidateLayout.of(Technique.PBT));
        assertSame(CandidateLayout.METAMORPHIC, CandidateLayout.of(Technique.MT));
    }

    /** A conformance run's candidate stream must not change, so the layout draws nothing. */
    @Test
    void theConformanceLayoutIsThePayloadAndDrawsNoRandomness() {
        var payload = new byte[] {1, 2, 3};
        var random = new SplittableRandom(7);
        assertSame(payload, CandidateLayout.CONFORMANCE.encode(payload, random));
        assertEquals(new SplittableRandom(7).nextLong(), random.nextLong());
    }

    /**
     * Read as a metamorphic input, a random array's header almost always claims more bytes than
     * follow it, so the rewrite part would be empty. The layout keeps the payload as the base and
     * always leaves rewrite bytes.
     */
    @Test
    void theMetamorphicLayoutKeepsThePayloadAsTheBaseAndAddsRewriteBytes() {
        var random = new SplittableRandom(11);
        for (var length : new int[] {0, 1, 17, 10240}) {
            var payload = new byte[length];
            random.nextBytes(payload);
            var input = CandidateLayout.METAMORPHIC.encode(payload, random);
            var offset = MetamorphicPayload.rewriteOffset(input);
            assertArrayEquals(payload, Arrays.copyOfRange(input, 2, offset));
            var rewrite = MetamorphicPayload.split(new Draw(input)).rewrite().remaining();
            assertTrue(rewrite >= 1 && rewrite <= CandidateLayout.MAXIMUM_REWRITE_BYTES, "rewrite bytes: " + rewrite);
        }
    }
}
