package io.github.tlaplus.hardening.workflow.library;

import static org.junit.jupiter.api.Assertions.*;

import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.MetamorphicConfig;
import io.github.tlaplus.hardening.config.OperatorLibraryConfig;
import io.github.tlaplus.hardening.config.RuleLibraryConfig;
import io.github.tlaplus.hardening.config.ToolJar;
import io.github.tlaplus.hardening.config.ToolsConfig;
import io.github.tlaplus.hardening.workflow.StagedTools;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LibraryInstallTest {
    private static final List<OperatorLibraryConfig.Module> MODULES =
            List.of(new OperatorLibraryConfig.Module("A", List.of("X")));

    @Test
    void copiesFilesWithTheirVersionAndDirectoriesIntoTheCorpus(@TempDir Path directory) throws Exception {
        var jar = Files.writeString(Files.createDirectories(directory.resolve("lib")).resolve("Mods.jar"), "jar");
        Files.writeString(directory.resolve("lib/VERSION"), "tag 1\n");
        var sources = Files.createDirectories(directory.resolve("tla-src"));
        Files.writeString(sources.resolve("A.tla"), "a");
        Files.createDirectory(sources.resolve("nested"));
        var library = new OperatorLibraryConfig(List.of(jar, sources), MODULES);

        var plan = LibraryInstall.plan(FuzzTlaConfig.defaults().withLibraries(library));
        var corpus = Files.createDirectory(directory.resolve("corpus"));
        LibraryInstall.copy(plan, corpus);

        var installed = plan.config().libraries();
        assertEquals(List.of(Path.of("tla/Mods.jar"), Path.of("tla/tla-src")), installed.classpath());
        assertEquals(MODULES, installed.modules());
        assertEquals("jar", Files.readString(corpus.resolve("tla/Mods.jar")));
        assertEquals("tag 1\n", Files.readString(corpus.resolve("tla/VERSION")));
        assertEquals("a", Files.readString(corpus.resolve("tla/tla-src/A.tla")));
        assertFalse(Files.exists(corpus.resolve("tla/tla-src/nested")));
        assertThrows(FileAlreadyExistsException.class, () -> LibraryInstall.copy(plan, corpus));
    }

    /**
     * The rewrite rules and the edge guards are copied like an operator library, once when they
     * share a directory, and every tool JAR is copied from the staged release (ADR 0025 §2, §3).
     */
    @Test
    void copiesTheRuleModulesAndTheToolsIntoTheCorpus(@TempDir Path directory) throws Exception {
        var rules = Files.createDirectories(directory.resolve("rewrites"));
        Files.writeString(rules.resolve("Rewrites.tla"), "rules");
        var module = Optional.of(new RuleLibraryConfig.RuleModule("Rewrites", List.of(rules)));
        var defaults = FuzzTlaConfig.defaults();
        var config = defaults
                .withMetamorphic(new MetamorphicConfig(new RuleLibraryConfig(module, Map.of("AddSub", 3)),
                        defaults.metamorphic().limits(), defaults.metamorphic().adoption()))
                .withGraphGuards(new RuleLibraryConfig(module, Map.of()));

        var plan = LibraryInstall.plan(config);
        var corpus = Files.createDirectory(directory.resolve("corpus"));
        LibraryInstall.copy(plan, corpus);

        var installed = new RuleLibraryConfig.RuleModule("Rewrites", List.of(Path.of("tla/rewrites")));
        assertEquals(new RuleLibraryConfig(Optional.of(installed), Map.of("AddSub", 3)), plan.config().metamorphic().rules());
        assertEquals(new RuleLibraryConfig(Optional.of(installed), Map.of()), plan.config().graphGuards());
        assertEquals("rules", Files.readString(corpus.resolve("tla/rewrites/Rewrites.tla")));
        assertEquals(ToolsConfig.installed(), plan.config().tools());
        for (var tool : ToolJar.values()) {
            assertEquals(Files.size(StagedTools.locate(tool)), Files.size(corpus.resolve("tools").resolve(tool.fileName())));
        }
    }

    /** A corpus whose configuration names its own tool JARs passes those on, as a base corpus does. */
    @Test
    void copiesConfiguredToolJars(@TempDir Path directory) throws Exception {
        var apalache = Files.writeString(directory.resolve("apalache.jar"), "old apalache");
        var config = FuzzTlaConfig.defaults().withTools(new ToolsConfig(Map.of(ToolJar.APALACHE, apalache)));

        var plan = LibraryInstall.plan(config);

        assertEquals(Path.of("tools/apalache.jar"), plan.tools().get(apalache));
        assertEquals(Path.of("tools/tla2tools.jar"), plan.tools().get(StagedTools.locate(ToolJar.TLA2TOOLS)));
    }

    @Test
    void rejectsMissingAndClashingEntries(@TempDir Path directory) throws Exception {
        var missing = new OperatorLibraryConfig(List.of(directory.resolve("Absent.jar")), MODULES);
        assertThrows(WorkflowException.class, () -> LibraryInstall.plan(FuzzTlaConfig.defaults().withLibraries(missing)));
        var first = Files.writeString(Files.createDirectories(directory.resolve("a")).resolve("M.jar"), "1");
        var second = Files.writeString(Files.createDirectories(directory.resolve("b")).resolve("M.jar"), "2");
        var clash = new OperatorLibraryConfig(List.of(first, second), MODULES);
        assertThrows(WorkflowException.class, () -> LibraryInstall.plan(FuzzTlaConfig.defaults().withLibraries(clash)));
    }
}
