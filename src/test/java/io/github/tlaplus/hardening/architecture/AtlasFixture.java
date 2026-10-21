package io.github.tlaplus.hardening.architecture;

import io.github.tlaplus.hardening.architecture.extract.AtlasSettings;
import io.github.tlaplus.hardening.architecture.model.ArchModel;
import io.github.tlaplus.hardening.architecture.model.AtlasJson;
import io.github.tlaplus.hardening.common.FileTrees;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** One atlas build of the main sources, shared by the test classes that read it. */
final class AtlasFixture {
    private static Path output;
    private static ArchModel model;

    private AtlasFixture() {}

    /** The directory the shared build wrote, built on first use. */
    static synchronized Path output() {
        if (output == null) {
            try {
                var directory = Files.createTempDirectory(Path.of("target").toAbsolutePath(), "atlas-test");
                Runtime.getRuntime().addShutdownHook(new Thread(() -> delete(directory)));
                var settings = AtlasSettings.defaults(Path.of("").toAbsolutePath()).withOutput(directory.resolve("atlas"));
                AtlasMain.run(settings);
                model = AtlasJson.read(settings.output().resolve(AtlasJson.MODEL_FILE));
                output = settings.output();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return output;
    }

    private static void delete(Path directory) {
        try {
            FileTrees.deleteRecursively(directory);
        } catch (IOException e) {
            // A leftover directory under target/ is harmless; mvn clean removes it.
        }
    }

    /** The model the shared build wrote, read back from its {@value AtlasJson#MODEL_FILE}. */
    static synchronized ArchModel model() {
        output();
        return model;
    }
}
