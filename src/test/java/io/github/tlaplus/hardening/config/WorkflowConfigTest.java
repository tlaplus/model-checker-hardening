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
                        1_000, new StageConfig(1_000), new ParserConfig(1_000, 30)),
                WorkflowConfig.defaults());
    }

    @Test
    void rejectsInvalidCapacitiesAndTimeouts() {
        assertThrows(IllegalArgumentException.class, () -> new StageConfig(-1));
        assertThrows(IllegalArgumentException.class, () -> new ParserConfig(1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowConfig(1, new StageConfig(2), new ParserConfig(1, 30)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkflowConfig(1, new StageConfig(1), new ParserConfig(2, 30)));
    }

    @Test
    void validatesTheGlobalTargetAgainstTheFinitePbtInputSpace() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new FuzzTlaConfig(
                        IrGenerationConfig.defaults(),
                        new WorkflowConfig(
                                2, new StageConfig(2), new ParserConfig(2, 30)),
                        new PbtConfig(0)));
    }
}
