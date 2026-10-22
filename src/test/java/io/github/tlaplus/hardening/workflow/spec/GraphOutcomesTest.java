package io.github.tlaplus.hardening.workflow.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.ExpectedOutcome;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.IrGenerators;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** A graph entry predicts the outcome its graph determines (ADR 0023 §4). */
class GraphOutcomesTest {
    private final GraphOutcomes outcomes = new GraphOutcomes(SpecDecoders.of(IrGenerationConfig.defaults()));

    @Test
    void aGraphEntryPredictsWhatItsGraphDetermines() {
        var random = new Random(41);
        var counterexamples = 0;
        for (var sample = 0; sample < 100; sample++) {
            var input = new byte[random.nextInt(16, 256)];
            random.nextBytes(input);
            var graph = IrGenerators.graphs(IrGenerationConfig.defaults()).generate(input);
            var distance = graph.targetDistance();
            var expected = distance.isPresent() ? ExpectedOutcome.counterexample(distance.getAsInt()) : ExpectedOutcome.pass();
            assertEquals(expected, GraphOutcomes.of(graph));
            assertEquals(Optional.of(expected), outcomes.expected(new CorpusInput(InputKind.GRAPH, input)));
            counterexamples += distance.isPresent() ? 1 : 0;
        }
        assertTrue(counterexamples > 10, "counterexamples: " + counterexamples);
    }

    @Test
    void anEntryOfAnotherKindPredictsNothing() {
        assertEquals(Optional.empty(), outcomes.expected(new CorpusInput(InputKind.MODULE, new byte[] {1, 2})));
        assertEquals(Optional.empty(), outcomes.expected(new CorpusInput(InputKind.EXPRESSION, new byte[0])));
    }

    @Test
    void theEmptyGraphViolatesItsInvariantInTheInitialState() {
        assertEquals(Optional.of(ExpectedOutcome.counterexample(0)),
                outcomes.expected(new CorpusInput(InputKind.GRAPH, new byte[0])));
    }
}
