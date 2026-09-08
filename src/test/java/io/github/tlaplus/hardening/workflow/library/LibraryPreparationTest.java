package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.config.*;
import io.github.tlaplus.hardening.corpus.CorpusDirectory;
import io.github.tlaplus.hardening.gen.library.OperatorId;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LibraryPreparationTest {
    static FuzzTlaConfig config(List<Path> classpath, String module, String... operators) {
        var defaults = FuzzTlaConfig.defaults();
        return new FuzzTlaConfig(defaults.generatedKind(), defaults.generator(), defaults.workflow(), defaults.pbt(),
                new OperatorLibraryConfig(classpath, List.of(new OperatorLibraryConfig.Module(module, List.of(operators)))));
    }

    @Test
    void emptySelectionsDoNotLaunchApalacheOrInspectTheClasspath() throws Exception {
        var defaults = FuzzTlaConfig.defaults();
        var config = new FuzzTlaConfig(defaults.generatedKind(), defaults.generator(), defaults.workflow(), defaults.pbt(),
                new OperatorLibraryConfig(List.of(Path.of("does-not-exist")), List.of()));
        assertSame(defaults.generator(), LibraryPreparation.prepare(config));
    }

    @Test
    void jarResourcesAndTransitiveDependenciesUseTheFirstSearchPath(@TempDir Path directory) throws Exception {
        var jar = directory.resolve("operators.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (var module : List.of("PolyOps", "CustomOperators")) {
                zip.putNextEntry(new ZipEntry("tla2sany/StandardModules/" + module + ".tla"));
                zip.write(Files.readAllBytes(Path.of("src/test/resources/custom/" + module + ".tla")));
                zip.closeEntry();
            }
        }
        var bad = Files.createDirectory(directory.resolve("bad"));
        Files.writeString(bad.resolve("CustomOperators.tla"), "not a module");
        var prepared = LibraryPreparation.prepare(config(List.of(jar, bad), "PolyOps", "Wrapped", "Empty"));
        assertFalse(prepared.library().get(new OperatorId("PolyOps", "Wrapped")).signature().isMono());
        assertFalse(prepared.library().get(new OperatorId("PolyOps", "Empty")).signature().isMono());
        assertTrue(prepared.library().replayManifest().contains("source CustomOperators.tla"));
        assertThrows(WorkflowException.class,
                () -> LibraryPreparation.prepare(config(List.of(bad, jar), "PolyOps", "Wrapped")));
    }

    @Test
    void classpathResourceLayoutHasPinnedPrecedenceAndCannotOverrideToolModules(@TempDir Path directory) throws Exception {
        var jar = directory.resolve("resources.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (var name : List.of("Mine.tla", "tla2sany/StandardModules/Mine.tla",
                    "Integers.tla", "__rewire_sequences_in_apalache.tla")) {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        var snapshot = Files.createDirectory(directory.resolve("snapshot"));
        LibrarySources.snapshot(List.of(jar), snapshot,
                io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution.locate());
        assertEquals("tla2sany/StandardModules/Mine.tla", Files.readString(snapshot.resolve("Mine.tla")));
        assertFalse(Files.exists(snapshot.resolve("Integers.tla")));
        assertFalse(Files.exists(snapshot.resolve("__rewire_sequences_in_apalache.tla")));
    }

    @Test
    void malformedSourcesMissingOperatorsAndUnsupportedEffectsArePreparationFailures(@TempDir Path directory) throws Exception {
        assertThrows(WorkflowException.class,
                () -> LibraryPreparation.prepare(config(List.of(directory), "Missing", "Op")));
        var source = directory.resolve("Mine.tla");
        for (var body : List.of("Op(x) == x'", "CONSTANT C\nOp(x) == C", "ASSUME TRUE\nOp(x) == x",
                "Op(F(_), x) == F(x)", "Op(x) == x + TRUE")) {
            Files.writeString(source, "---- MODULE Mine ----\nEXTENDS Integers\n" + body + "\n====\n");
            assertThrows(WorkflowException.class, () -> LibraryPreparation.prepare(config(List.of(directory), "Mine", "Op")), body);
        }
        Files.writeString(source, "---- MODULE Mine ----\nOther(x) == x\n====\n");
        assertTrue(assertThrows(WorkflowException.class,
                () -> LibraryPreparation.prepare(config(List.of(directory), "Mine", "Missing")))
                .getMessage().contains("Missing"));
    }

    @Test
    void manifestsPinSourcesSelectionsAndToolWhileAllowingClasspathRelocation(@TempDir Path directory) throws Exception {
        var sources = Files.createDirectory(directory.resolve("sources"));
        var source = sources.resolve("Mine.tla");
        Files.writeString(source, "---- MODULE Mine ----\nOp(x) == x\n====\n");
        var config = config(List.of(sources), "Mine", "Op");
        var initial = LibraryPreparation.prepare(config).library().replayManifest();
        var corpus = CorpusDirectory.initialize(directory.resolve("corpus"), TomlConfig.render(config));
        try (var lock = corpus.acquireExclusiveLock()) { LibraryManifest.verify(corpus, initial, true); }
        LibraryManifest.verify(corpus, initial, false);
        assertEquals(initial, LibraryPreparation.prepare(config).library().replayManifest());
        var relocated = Files.createDirectory(directory.resolve("relocated"));
        Files.copy(source, relocated.resolve("Mine.tla"));
        assertEquals(initial, LibraryPreparation.prepare(config(List.of(relocated), "Mine", "Op"))
                .library().replayManifest());
        Files.writeString(source, "---- MODULE Mine ----\nOp(x) == {x}\n====\n");
        var changed = LibraryPreparation.prepare(config).library().replayManifest();
        assertThrows(WorkflowException.class, () -> LibraryManifest.verify(corpus, changed, false));
        assertThrows(WorkflowException.class, () -> LibraryManifest.verify(corpus, "", false));
        assertThrows(WorkflowException.class, () -> LibraryManifest.verify(
                CorpusDirectory.initialize(directory.resolve("unrecorded"), TomlConfig.render(config)), initial, false));
    }
}
