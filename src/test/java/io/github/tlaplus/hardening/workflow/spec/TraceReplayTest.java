package io.github.tlaplus.hardening.workflow.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.github.tlaplus.hardening.corpus.CorpusInput;
import io.github.tlaplus.hardening.corpus.ExpectedOutcome;
import io.github.tlaplus.hardening.gen.InputKind;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.trace.Trace;
import io.github.tlaplus.hardening.gen.trace.TraceEnding;
import io.github.tlaplus.hardening.gen.trace.TracePayload;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The decoder, module and prediction of trace entries (ADR 0034 §2–§4). */
class TraceReplayTest {
    private final SpecDecoders decoders = SpecDecoders.of(IrGenerationConfig.defaults());

    @Test
    void replaysATraceWithTheTracesAssignmentsFirst() {
        var artifact = decoders.decode(ReplayFixtures.module(new TraceEnding.Invariant(), false, 0, false, 1));
        var text = compact(SpecText.render(artifact));

        assertEquals(CheckRequest.invariant(1), artifact.request());
        assertEquals(2, artifact.trace().orElseThrow().states().size());
        assertTrue(text.contains("BaseInit==var0=FALSE/\\step=0"), text);
        assertTrue(text.contains("Init==var0=FALSE/\\step=0/\\idx=0/\\BaseInit"), text);
        assertTrue(text.contains("Next==idx=0/\\var0'=FALSE/\\step'=1/\\idx'=1/\\BaseNext"), text);
        assertTrue(text.contains("Inv==idx/=1\\/BaseInv"), text);
        assertTrue(text.contains("Prop==TRUE"), text);
    }

    @Test
    void comparesTheLastStateOfALassoWithItsLoopState() {
        var text = compact(SpecText.render(decoders.decode(
                ReplayFixtures.module(new TraceEnding.Loop(1), false, 0, false, 1, false, 1))));

        assertTrue(text.contains("Inv==idx/=2\\/<<var0,step>>/=<<FALSE,1>>"), text);
    }

    @Test
    void checksOnlyReachabilityOfAPrefix() {
        var text = compact(SpecText.render(decoders.decode(ReplayFixtures.module(new TraceEnding.Prefix(), false, 0))));

        assertTrue(text.contains("Inv==idx/=0"), text);
        assertTrue(text.contains("Next==UNCHANGED(<<var0,step,idx>>)"), text);
    }

    @Test
    void rejectsATraceOfAnotherWidthOrType() {
        var nodes = JsonNodeFactory.instance;
        var narrow = new TracePayload(InputKind.MODULE, new byte[0],
                new Trace(List.of(List.of(nodes.booleanNode(false))), new TraceEnding.Prefix()));
        var mistyped = new TracePayload(InputKind.MODULE, new byte[0],
                new Trace(List.of(List.of(nodes.numberNode(0), nodes.numberNode(0))), new TraceEnding.Prefix()));

        for (var payload : List.of(narrow, mistyped)) {
            assertThrows(InputRejectedException.class,
                    () -> decoders.decode(new CorpusInput(InputKind.TRACE, payload.encode())));
        }
    }

    /** The printer's spacing around operators is not ours to pin, so whitespace is dropped. */
    private static String compact(String text) {
        return text.replaceAll("\\s+", "");
    }

    @Test
    void predictsACounterexampleOfTheTracesLength() {
        var outcomes = new PredictedOutcomes(decoders);

        assertEquals(Optional.of(ExpectedOutcome.counterexample(10)),
                outcomes.expected(ReplayFixtures.graph(new TraceEnding.Invariant(), 10)));
        assertEquals(Optional.of(ExpectedOutcome.counterexample(0)),
                outcomes.expected(ReplayFixtures.module(new TraceEnding.Invariant(), false, 0)));
    }
}
