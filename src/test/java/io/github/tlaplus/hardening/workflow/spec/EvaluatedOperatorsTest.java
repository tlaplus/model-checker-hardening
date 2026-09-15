package io.github.tlaplus.hardening.workflow.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.signature.IrOperatorCounts;
import java.util.Random;
import org.junit.jupiter.api.Test;

class EvaluatedOperatorsTest {
    private final SpecDecoders decoders = SpecDecoders.of(IrGenerationConfig.defaults());

    @Test
    void countsTheEvaluatedCodeOfTheReplayedModule() {
        var bytes = new byte[4_096];
        new Random(42).nextBytes(bytes);
        var input = new CorpusInput(InputKind.MODULE, bytes);

        var counts = new EvaluatedOperators(decoders).count(input);

        assertFalse(counts.operators().isEmpty());
        assertTrue(counts.nodes() >= counts.operators().values().stream().mapToLong(Long::longValue).sum());
        assertEquals(
                IrOperatorCounts.evaluated(decoders.decode(input).module(), FuzzInputModule.ENTRY_POINTS),
                counts);
        assertEquals(counts, new EvaluatedOperators(decoders).count(input));
    }

    @Test
    void replaysAnExpressionInput() {
        var counts = new EvaluatedOperators(decoders).count(new CorpusInput(InputKind.EXPRESSION, new byte[0]));

        assertTrue(counts.nodes() > 0);
    }
}
