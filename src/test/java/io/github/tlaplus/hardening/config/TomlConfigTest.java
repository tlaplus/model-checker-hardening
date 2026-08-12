package io.github.tlaplus.hardening.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TomlConfigTest {
    @Test
    void renderedDefaultsRoundTripThroughTheStrictParser(@TempDir Path directory) throws Exception {
        var path = directory.resolve("config.toml");
        TomlConfig.writeNew(path, FuzzTlaConfig.defaults());

        assertEquals(FuzzTlaConfig.defaults(), TomlConfig.read(path));
        assertTrue(Files.readString(path).contains("corpus_entries = 1000"));
        assertTrue(Files.readString(path).contains("maximum_input_bytes = 1024"));
    }

    @Test
    void reportsMalformedToml(@TempDir Path directory) throws Exception {
        var failure = assertInvalid(directory, "[generator\n");

        assertTrue(failure.getMessage().contains("invalid TOML"));
    }

    @Test
    void rejectsMissingAndUnknownKeys(@TempDir Path directory) throws Exception {
        var missing = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace("maximum_nodes = 16\n", ""));
        assertTrue(missing.getMessage().contains("missing generator keys: maximum_nodes"));

        var unknown = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace("[pbt]", "unexpected = 1\n\n[pbt]"));
        assertTrue(unknown.getMessage().contains("unknown generator keys: unexpected"));
    }

    @Test
    void rejectsMissingTablesAndUnexpectedRootKeys(@TempDir Path directory) throws Exception {
        var missing = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace("[pbt]", "[not_pbt]"));
        assertTrue(missing.getMessage().contains("missing root keys: pbt"));

        var unexpected = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults()) + "\n[extra]\nvalue = 1\n");
        assertTrue(unexpected.getMessage().contains("unknown root keys: extra"));
    }

    @Test
    void rejectsWrongTypesAndOutOfRangeIntegers(@TempDir Path directory) throws Exception {
        var wrongType = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace("corpus_entries = 1000", "corpus_entries = \"many\""));
        assertTrue(wrongType.getMessage().contains("pbt.corpus_entries"));

        var tooLarge = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace("maximum_input_bytes = 1024", "maximum_input_bytes = 2147483648"));
        assertTrue(tooLarge.getMessage().contains("outside the supported integer range"));
    }

    @Test
    void reportsRecordValidationFailuresAsConfigurationErrors(@TempDir Path directory)
            throws Exception {
        var invalidGenerator = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace("maximum_nodes = 16", "maximum_nodes = 0"));
        assertTrue(invalidGenerator.getMessage().contains("maximumNodes must be positive"));

        var impossibleCorpus = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace("maximum_input_bytes = 1024", "maximum_input_bytes = 0"));
        assertTrue(impossibleCorpus.getMessage().contains("distinct bounded inputs"));
    }

    private ConfigException assertInvalid(Path directory, String contents) throws Exception {
        var path = directory.resolve("config-" + System.nanoTime() + ".toml");
        Files.writeString(path, contents);
        return assertThrows(ConfigException.class, () -> TomlConfig.read(path));
    }
}
