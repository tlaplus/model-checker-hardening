package io.github.tlaplus.hardening.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tlaplus.hardening.corpus.CorpusStage;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowConfigTest {
    @Test
    void exposesDocumentedDefaults() {
        assertEquals(
                new WorkflowConfig(
                        1_000,
                        new StageConfig(1_000),
                        new ParserStageConfig(1_000, 30),
                        Map.of(
                                CorpusStage.TLC,
                                new CheckerStageConfig(1_000, 30, 512, 1),
                                CorpusStage.APALACHE,
                                new CheckerStageConfig(
                                1_000,
                                30,
                                1_024,
                                CheckerStageConfig.DEFAULT_APALACHE_WORKERS))),
                WorkflowConfig.defaults());
        assertEquals(
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
                CheckerStageConfig.DEFAULT_APALACHE_WORKERS);
    }

    @Test
    void rejectsInvalidCapacitiesAndTimeouts() {
        assertThrows(IllegalArgumentException.class, () -> new StageConfig(-1));
        assertThrows(IllegalArgumentException.class, () -> new ParserStageConfig(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CheckerStageConfig(1, 0, 512, 1));
        assertThrows(IllegalArgumentException.class, () -> new CheckerStageConfig(1, 30, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CheckerStageConfig(1, 30, 512, 0));
        assertThrows(IllegalArgumentException.class, () -> new CheckerStageConfig(1, 0, 512, 1));
        assertThrows(IllegalArgumentException.class, () -> new CheckerStageConfig(1, 30, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CheckerStageConfig(1, 30, 512, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowConfig(
                        1,
                        new StageConfig(2),
                        new ParserStageConfig(1, 30),
                        Map.of(
                                CorpusStage.TLC,
                                new CheckerStageConfig(1, 30, 512, 1),
                                CorpusStage.APALACHE,
                                new CheckerStageConfig(1, 30, 512, 1))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowConfig(
                        1,
                        new StageConfig(1),
                        new ParserStageConfig(2, 30),
                        Map.of(
                                CorpusStage.TLC,
                                new CheckerStageConfig(1, 30, 512, 1),
                                CorpusStage.APALACHE,
                                new CheckerStageConfig(1, 30, 512, 1))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowConfig(
                        1,
                        new StageConfig(1),
                        new ParserStageConfig(1, 30),
                        Map.of(
                                CorpusStage.TLC,
                                new CheckerStageConfig(2, 30, 512, 1),
                                CorpusStage.APALACHE,
                                new CheckerStageConfig(1, 30, 512, 1))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowConfig(
                        1,
                        new StageConfig(1),
                        new ParserStageConfig(1, 30),
                        Map.of(
                                CorpusStage.TLC,
                                new CheckerStageConfig(1, 30, 512, 1),
                                CorpusStage.APALACHE,
                                new CheckerStageConfig(2, 30, 512, 1))));
    }

    @Test
    void validatesTheGlobalTargetAgainstTheFinitePbtInputSpace() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FuzzTlaConfig(
                        IrGenerationConfig.defaults(),
                        new WorkflowConfig(
                                2,
                                new StageConfig(2),
                                new ParserStageConfig(2, 30),
                                Map.of(
                                        CorpusStage.TLC,
                                        new CheckerStageConfig(2, 30, 512, 1),
                                        CorpusStage.APALACHE,
                                        new CheckerStageConfig(2, 30, 512, 1))),
                        new PbtConfig(0, 10, 2.0, 1.5)));
    }
}
