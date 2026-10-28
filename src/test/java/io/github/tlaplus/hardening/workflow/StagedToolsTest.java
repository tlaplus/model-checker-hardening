package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.config.ToolJar;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class StagedToolsTest {
    /** The build stages every tool JAR that init copies into a corpus (ADR 0025 §3). */
    @Test
    void theBuildStagesEveryToolJar() throws Exception {
        for (var tool : ToolJar.values()) {
            var jar = StagedTools.locate(tool);
            assertTrue(Files.isRegularFile(jar), jar.toString());
            assertEquals(tool.fileName(), jar.getFileName().toString());
        }
    }
}
