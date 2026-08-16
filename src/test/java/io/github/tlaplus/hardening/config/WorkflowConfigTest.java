package io.github.tlaplus.hardening.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import org.junit.jupiter.api.Test;

class WorkflowConfigTest {
    @Test
    void exposesDocumentedDefaults() {
        assertEquals(
                new WorkflowConfig(
                        1_000,
                        new StageConfig(1_000),
                        new ParserStageConfig(1_000, 30),
                        new TlcStageConfig(1_000, 30, 512, 1)),
                WorkflowConfig.defaults());
    }

    @Test
    void rejectsInvalidCapacitiesAndTimeouts() {
        assertThrows(IllegalArgumentException.class, () -> new StageConfig(-1));
        assertThrows(IllegalArgumentException.class, () -> new ParserStageConfig(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TlcStageConfig(1, 0, 512, 1));
        assertThrows(IllegalArgumentException.class, () -> new TlcStageConfig(1, 30, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new TlcStageConfig(1, 30, 512, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowConfig(
                        1,
                        new StageConfig(2),
                        new ParserStageConfig(1, 30),
                        new TlcStageConfig(1, 30, 512, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowConfig(
                        1,
                        new StageConfig(1),
                        new ParserStageConfig(2, 30),
                        new TlcStageConfig(1, 30, 512, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowConfig(
                        1,
                        new StageConfig(1),
                        new ParserStageConfig(1, 30),
                        new TlcStageConfig(2, 30, 512, 1)));
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
                                new TlcStageConfig(2, 30, 512, 1)),
                        new PbtConfig(0, 10, 2.0, 1.5)));
    }
}
