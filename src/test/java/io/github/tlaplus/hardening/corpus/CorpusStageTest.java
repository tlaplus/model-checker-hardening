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
    void aggregatorHasNoCrashResult() {
        assertEquals(
                List.of(CorpusVerdict.PASS, CorpusVerdict.FAIL),
                CorpusStage.AGGREGATOR.resultVerdicts());
    }
}
