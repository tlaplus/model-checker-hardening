package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.gen.library.OperatorId;
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
        var first = new OperatorId("A", "First");
        var second = new OperatorId("A", "Second");
        var manifest = LibraryManifest.create(sources, tool, List.of(second, first));
        assertEquals("""
                fuzztla-library-v1
                apalache e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
                source A.tla ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb
                source B.tla 3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d
                operator A!Second
                operator A!First
                """, manifest);
        assertNotEquals(manifest, LibraryManifest.create(sources, tool, List.of(first, second)));
        assertNotEquals(manifest, LibraryManifest.create(sources, tool, List.of(second)));
    }
}
