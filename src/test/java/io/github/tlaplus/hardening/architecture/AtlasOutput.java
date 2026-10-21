package io.github.tlaplus.hardening.architecture;

import io.github.tlaplus.hardening.architecture.model.AtlasJson;
import io.github.tlaplus.hardening.common.FileTrees;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** The atlas's output directory: emptied before a build, then filled with its pages. */
final class AtlasOutput {
    private AtlasOutput() {}

    /**
     * Empties {@code output}, replacing an earlier atlas there. Refuses a non-empty directory that
     * holds no {@value AtlasJson#MODEL_FILE}, so a mistyped path cannot erase other files.
     */
    static void prepare(Path output) throws IOException {
        if (Files.isDirectory(output)) {
            try (var entries = Files.list(output)) {
                if (entries.findAny().isPresent() && !Files.exists(output.resolve(AtlasJson.MODEL_FILE))) {
                    throw new IOException(output + " is not empty and holds no earlier atlas");
                }
            }
            FileTrees.deleteRecursively(output);
        }
        Files.createDirectories(output);
    }

    /** Writes each page at its path relative to {@code output}. */
    static void write(Path output, Map<String, String> pages) throws IOException {
        for (var page : pages.entrySet()) {
            var file = output.resolve(page.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, page.getValue(), StandardCharsets.UTF_8);
        }
    }
}
