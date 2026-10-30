package io.github.tlaplus.hardening.workflow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.OperatorLibraryConfig;
import io.github.tlaplus.hardening.config.ToolJar;
import io.github.tlaplus.hardening.config.ToolsConfig;
import io.github.tlaplus.hardening.gen.library.LibraryLinkage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpusToolsTest {
    @Test
    void aCorpusWithoutToolsRunsTheStagedRelease() throws Exception {
        var config = FuzzTlaConfig.defaults();
        CorpusTools.require(config);
        assertEquals(StagedTools.locate(ToolJar.APALACHE), CorpusTools.apalache(config));
        assertEquals(List.of(), CorpusTools.sourceCheckerClasspath(config));
    }

    /** The corpus's JARs run; its TLC follows the library classpath, which keeps its overrides first. */
    @Test
    void aCorpusRunsItsOwnJars(@TempDir Path directory) throws Exception {
        var apalache = Files.writeString(directory.resolve("apalache.jar"), "");
        var tla2tools = Files.writeString(directory.resolve("tla2tools.jar"), "");
        var library = Files.createDirectory(directory.resolve("library"));
        var config = FuzzTlaConfig.defaults()
                .withLibraries(new OperatorLibraryConfig(List.of(library), List.of(new OperatorLibraryConfig.Module(
                        "M", List.of("Op"), LibraryLinkage.INSTANCE))))
                .withTools(new ToolsConfig(Map.of(ToolJar.APALACHE, apalache, ToolJar.TLA2TOOLS, tla2tools)));

        CorpusTools.require(config);
        assertEquals(apalache, CorpusTools.apalache(config));
        assertEquals(List.of(library, tla2tools), CorpusTools.sourceCheckerClasspath(config));
    }

    @Test
    void refusesAMissingJar(@TempDir Path directory) {
        var missing = directory.resolve("tools/apalache.jar");
        var config = FuzzTlaConfig.defaults().withTools(new ToolsConfig(Map.of(ToolJar.APALACHE, missing)));

        var refused = assertThrows(WorkflowException.class, () -> CorpusTools.require(config));
        assertTrue(refused.getMessage().contains("tools.apalache does not exist"), refused.getMessage());
    }
}
