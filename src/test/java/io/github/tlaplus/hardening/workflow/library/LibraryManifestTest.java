package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.config.OperatorLibraryConfig;
import io.github.tlaplus.hardening.gen.library.LibraryLinkage;
import io.github.tlaplus.hardening.gen.library.ModuleLink;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LibraryManifestTest {
    @Test
    void pinsSourceOrderSelectionOrderAndWireFormat(@TempDir Path directory) throws Exception {
        var sources = Files.createDirectory(directory.resolve("sources"));
        Files.writeString(sources.resolve("B.tla"), "b");
        Files.writeString(sources.resolve("A.tla"), "a");
        var tool = directory.resolve("tool.jar");
        Files.write(tool, new byte[0]);
        var manifest = LibraryManifest.create(sources, tool, library(List.of(), LibraryLinkage.INLINE, "Second", "First"));
        assertEquals("""
                fuzztla-library-v1
                apalache e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
                source A.tla ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb
                source B.tla 3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d
                operator A!Second
                operator A!First
                """, manifest);
        assertNotEquals(manifest, LibraryManifest.create(sources, tool, library(List.of(), LibraryLinkage.INLINE, "First", "Second")));
        assertNotEquals(manifest, LibraryManifest.create(sources, tool, library(List.of(), LibraryLinkage.INLINE, "Second")));
    }

    @Test
    void pinsLinkageAndClasspathFilesOfInstanceLinkedLibraries(@TempDir Path directory) throws Exception {
        var sources = Files.createDirectory(directory.resolve("sources"));
        Files.writeString(sources.resolve("A.tla"), "a");
        var tool = directory.resolve("tool.jar");
        Files.write(tool, new byte[0]);
        var jar = directory.resolve("lib.jar");
        Files.writeString(jar, "b");
        var classpath = List.of(jar, sources);
        var manifest = LibraryManifest.create(sources, tool, library(classpath, LibraryLinkage.INSTANCE, "Op"));
        assertEquals("""
                fuzztla-library-v1
                apalache e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
                source A.tla ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb
                classpath lib.jar 3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d
                operator A!Op instance
                """, manifest);
        assertFalse(LibraryManifest.create(sources, tool, library(classpath, LibraryLinkage.INLINE, "Op"))
                .contains("classpath"));
    }

    @Test
    void namesTheTlcModuleOfDiffLinkedLibraries(@TempDir Path directory) throws Exception {
        var sources = Files.createDirectory(directory.resolve("sources"));
        Files.writeString(sources.resolve("A.tla"), "a");
        var tool = directory.resolve("tool.jar");
        Files.write(tool, new byte[0]);
        var jar = directory.resolve("lib.jar");
        Files.writeString(jar, "b");
        var library = new OperatorLibraryConfig(List.of(jar), List.of(new OperatorLibraryConfig.Module(
                "A", List.of("Op"), new ModuleLink(LibraryLinkage.DIFF, "ATLC"))));
        assertEquals("""
                fuzztla-library-v1
                apalache e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
                source A.tla ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb
                classpath lib.jar 3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d
                operator A!Op diff ATLC
                """, LibraryManifest.create(sources, tool, library));
    }

    private static OperatorLibraryConfig library(List<Path> classpath, LibraryLinkage linkage, String... operators) {
        return new OperatorLibraryConfig(classpath,
                List.of(new OperatorLibraryConfig.Module("A", List.of(operators), linkage)));
    }
}
