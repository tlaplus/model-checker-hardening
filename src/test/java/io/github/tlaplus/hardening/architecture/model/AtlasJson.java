package io.github.tlaplus.hardening.architecture.model;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The JSON form of the atlas model: extraction writes it, and rendering and the tests read it
 * back (ADR 0022).
 */
public final class AtlasJson {
    /** The model's file name in an atlas directory. */
    public static final String MODEL_FILE = "model.json";

    /** The mapper of every atlas JSON document: the model and the settings the doclet receives. */
    public static final JsonMapper MAPPER =
            JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    private AtlasJson() {}

    public static void write(ArchModel model, Path file) throws IOException {
        Files.writeString(file, MAPPER.writeValueAsString(model) + "\n", StandardCharsets.UTF_8);
    }

    public static ArchModel read(Path file) throws IOException {
        return MAPPER.readValue(file.toFile(), ArchModel.class);
    }
}
