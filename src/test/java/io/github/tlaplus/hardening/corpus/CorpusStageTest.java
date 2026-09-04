package io.github.tlaplus.hardening.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CorpusStageTest {
    @Test
    void declarationOrderIsPipelineOrder() {
        assertEquals(
                List.of(
                        CorpusStage.PARSER,
                        CorpusStage.TLC,
                        CorpusStage.APALACHE,
                        CorpusStage.AGGREGATOR),
                List.of(CorpusStage.values()));
    }

    @Test
    void onlyCheckerStagesRecordCounterexamples() {
        assertEquals(
                List.of(CorpusVerdict.PASS, CorpusVerdict.FAIL, CorpusVerdict.CRASH),
                CorpusStage.PARSER.resultVerdicts());
        assertEquals(
                List.of(
                        CorpusVerdict.PASS,
                        CorpusVerdict.COUNTEREXAMPLE,
                        CorpusVerdict.FAIL,
                        CorpusVerdict.CRASH),
                CorpusStage.TLC.resultVerdicts());
        assertEquals(
                CorpusStage.TLC.resultVerdicts(),
                CorpusStage.APALACHE.resultVerdicts());
        assertEquals(
                List.of(CorpusVerdict.PASS, CorpusVerdict.FAIL),
                CorpusStage.AGGREGATOR.resultVerdicts());
    }
}
