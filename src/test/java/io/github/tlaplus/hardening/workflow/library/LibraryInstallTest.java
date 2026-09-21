package io.github.tlaplus.hardening.workflow.library;

import static org.junit.jupiter.api.Assertions.*;

import io.github.tlaplus.hardening.config.OperatorLibraryConfig;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

        var installed = LibraryInstall.installed(library);
        var corpus = Files.createDirectory(directory.resolve("corpus"));
        LibraryInstall.copy(library, corpus);

        assertEquals(List.of(Path.of("tla/Mods.jar"), Path.of("tla/tla-src")), installed.classpath());
        assertEquals(MODULES, installed.modules());
        assertEquals("jar", Files.readString(corpus.resolve("tla/Mods.jar")));
        assertEquals("tag 1\n", Files.readString(corpus.resolve("tla/VERSION")));
        assertEquals("a", Files.readString(corpus.resolve("tla/tla-src/A.tla")));
        assertFalse(Files.exists(corpus.resolve("tla/tla-src/nested")));
        assertThrows(FileAlreadyExistsException.class, () -> LibraryInstall.copy(library, corpus));
    }

    @Test
    void rejectsMissingAndClashingEntries(@TempDir Path directory) throws Exception {
        var missing = new OperatorLibraryConfig(List.of(directory.resolve("Absent.jar")), MODULES);
        assertThrows(WorkflowException.class, () -> LibraryInstall.installed(missing));
        var first = Files.writeString(Files.createDirectories(directory.resolve("a")).resolve("M.jar"), "1");
        var second = Files.writeString(Files.createDirectories(directory.resolve("b")).resolve("M.jar"), "2");
        var clash = new OperatorLibraryConfig(List.of(first, second), MODULES);
        assertThrows(WorkflowException.class, () -> LibraryInstall.installed(clash));
    }
}
