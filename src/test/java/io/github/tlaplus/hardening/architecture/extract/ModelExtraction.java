package io.github.tlaplus.hardening.architecture.extract;

import io.github.tlaplus.hardening.architecture.model.ArchModel;
import io.github.tlaplus.hardening.architecture.model.AtlasJson;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.tools.ToolProvider;

/**
 * Extracts the atlas model of the main sources: runs {@link ArchitectureDoclet} through the system
 * documentation tool, with this JVM's class path, and reads back the model it writes.
 */
public final class ModelExtraction {
    private ModelExtraction() {}

    /**
     * Writes the model to {@value AtlasJson#MODEL_FILE} in the settings' output directory, which
     * must exist, and returns it. Fails with the tool's diagnostics.
     */
    public static ArchModel extract(AtlasSettings settings) throws IOException {
        var tool = ToolProvider.getSystemDocumentationTool();
        var log = new StringWriter();
        try (var files = tool.getStandardFileManager(null, null, StandardCharsets.UTF_8);
                var sources = Files.walk(settings.sources())) {
            var units = files.getJavaFileObjectsFromPaths(
                    sources.filter(p -> p.toString().endsWith(".java")).sorted().toList());
            var options = new ArrayList<>(List.of("-private", "-quiet", "-encoding", "UTF-8",
                    "-classpath", System.getProperty("java.class.path")));
            options.addAll(SettingsOption.arguments(settings));
            var task = tool.getTask(
                    new PrintWriter(log), files, null, ArchitectureDoclet.class, options, units);
            if (!task.call()) {
                throw new IOException("the atlas doclet failed:\n" + log);
            }
        }
        return AtlasJson.read(settings.output().resolve(AtlasJson.MODEL_FILE));
    }
}
