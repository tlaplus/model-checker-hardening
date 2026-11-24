package io.github.tlaplus.hardening.workflow.library;

import static org.junit.jupiter.api.Assertions.*;

import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.GenericCheckerConfig;
import io.github.tlaplus.hardening.config.GenericCompatibility;
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
import java.nio.file.attribute.PosixFilePermissions;
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
     * The rewrite rules, the temporal rules and the edge guards are copied like an operator
     * library, once when they share a directory, and every tool JAR is copied from the staged
     * release (ADR 0025 §2, §3, ADR 0027 §5).
     */
    @Test
    void copiesTheRuleModulesAndTheToolsIntoTheCorpus(@TempDir Path directory) throws Exception {
        var rules = Files.createDirectories(directory.resolve("rewrites"));
        Files.writeString(rules.resolve("Rewrites.tla"), "rules");
        Files.writeString(rules.resolve("TemporalRewrites.tla"), "temporal rules");
        var module = Optional.of(new RuleLibraryConfig.RuleModule("Rewrites", List.of(rules)));
        var temporal = Optional.of(new RuleLibraryConfig.RuleModule("TemporalRewrites", List.of(rules)));
        var config = FuzzTlaConfig.defaults()
                .withMetamorphic(MetamorphicConfig.defaults()
                        .withRules(new RuleLibraryConfig(module, Map.of("AddSub", 3)))
                        .withTemporalRules(new RuleLibraryConfig(temporal, Map.of("WFDef", 4))))
                .withGraphGuards(new RuleLibraryConfig(module, Map.of()));

        var plan = LibraryInstall.plan(config);
        var corpus = Files.createDirectory(directory.resolve("corpus"));
        LibraryInstall.copy(plan, corpus);

        var installed = new RuleLibraryConfig.RuleModule("Rewrites", List.of(Path.of("tla/rewrites")));
        assertEquals(new RuleLibraryConfig(Optional.of(installed), Map.of("AddSub", 3)), plan.config().metamorphic().rules());
        assertEquals(new RuleLibraryConfig(Optional.of(installed), Map.of()), plan.config().graphGuards());
        assertEquals(new RuleLibraryConfig(Optional.of(new RuleLibraryConfig.RuleModule("TemporalRewrites",
                        List.of(Path.of("tla/rewrites")))), Map.of("WFDef", 4)),
                plan.config().metamorphic().temporalRules());
        assertEquals("rules", Files.readString(corpus.resolve("tla/rewrites/Rewrites.tla")));
        assertEquals("temporal rules", Files.readString(corpus.resolve("tla/rewrites/TemporalRewrites.tla")));
        assertEquals(ToolsConfig.none().installed(), plan.config().tools());
        for (var tool : ToolJar.values()) {
            assertEquals(Files.size(StagedTools.locate(tool)), Files.size(corpus.resolve("tools").resolve(tool.fileName())));
        }
    }

    /** A corpus whose configuration names its own tool JARs passes those on, as a base corpus does. */
    @Test
    void copiesConfiguredToolJars(@TempDir Path directory) throws Exception {
        var apalache = Files.writeString(directory.resolve("apalache.jar"), "old apalache");
        var config = FuzzTlaConfig.defaults().withTools(ToolsConfig.none().with(ToolJar.APALACHE, apalache));

        var plan = LibraryInstall.plan(config);

        assertEquals(Path.of("tools/apalache.jar"), plan.tools().get(apalache));
        assertEquals(Path.of("tools/tla2tools.jar"), plan.tools().get(StagedTools.locate(ToolJar.TLA2TOOLS)));
    }

    /** The generic checker's executable goes to tools/ under its own name, still executable (ADR 0033 §4). */
    @Test
    void copiesTheGenericCheckerExecutable(@TempDir Path directory) throws Exception {
        var executable = Files.writeString(directory.resolve("manyan-a9ca4f38028f"), "#!/bin/sh\n");
        Files.setPosixFilePermissions(executable, PosixFilePermissions.fromString("rwxr-xr-x"));
        var checker = new GenericCheckerConfig("Manyan", GenericCompatibility.TLC, executable, List.of("{module}"));
        var config = FuzzTlaConfig.defaults().withTools(ToolsConfig.none().withGeneric(checker));

        var plan = LibraryInstall.plan(config);
        var corpus = Files.createDirectory(directory.resolve("corpus"));
        LibraryInstall.copy(plan, corpus);

        var installed = Path.of("tools/manyan-a9ca4f38028f");
        assertEquals(installed, plan.tools().get(executable));
        assertEquals(Optional.of(checker.withExecutable(installed)), plan.config().tools().generic());
        assertTrue(Files.isExecutable(corpus.resolve(installed)));
    }

    /** A generic checker whose executable is missing, not executable, or named like a JAR is refused. */
    @Test
    void rejectsAnUnusableGenericExecutable(@TempDir Path directory) throws Exception {
        var plain = Files.writeString(directory.resolve("plain"), "");
        var jarNamed = Files.writeString(directory.resolve("apalache.jar"), "");
        Files.setPosixFilePermissions(jarNamed, PosixFilePermissions.fromString("rwxr-xr-x"));
        for (var executable : List.of(directory.resolve("absent"), plain, jarNamed)) {
            var checker = new GenericCheckerConfig("X", GenericCompatibility.TLC, executable, List.of());
            var config = FuzzTlaConfig.defaults().withTools(ToolsConfig.none().withGeneric(checker));
            assertThrows(WorkflowException.class, () -> LibraryInstall.plan(config), executable.toString());
        }
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
