package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.engine.CustomExpressionKind;
import io.github.tlaplus.hardening.gen.library.OperatorId;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class OperatorLibraryConfigTest {
    @Test
    void resolvesRelativePathsAndPreservesSelectionOrderAndCase(@TempDir Path directory) throws Exception {
        var text = TomlConfig.render(FuzzTlaConfig.defaults())
                .replace("classpath = []", "classpath = [\"./tla files\", \"../operators.jar\"]")
                .replace("custom_operators = []", "custom_operators = [{ module = \"Mine\", operators = [\"Second\", \"First\"] }]")
                .replace("weights = { name = 8, enum_set = 16 }", "weights = { \"Mine!Second\" = 12 }");
        var path = directory.resolve("config.toml");
        Files.writeString(path, text);
        var config = TomlConfig.read(path);
        assertEquals(directory.resolve("tla files"), config.libraries().classpath().getFirst());
        assertEquals(List.of(new OperatorId("Mine", "Second"), new OperatorId("Mine", "First")), config.libraries().operators());
        assertEquals(12, config.generator().weightOf(new CustomExpressionKind(new OperatorId("Mine", "Second"))));
        Files.writeString(path, TomlConfig.render(config));
        assertEquals(config, TomlConfig.read(path));
    }

    @Test
    void rejectsMalformedAndAmbiguousSelections(@TempDir Path directory) throws Exception {
        var base = TomlConfig.render(FuzzTlaConfig.defaults());
        var invalid = List.of(
                "[{ module = \"Mine\", operators = [] }]",
                "[{ module = \"Mine\", operators = [\"Op\", \"Op\"] }]",
                "[{ module = \"../Mine\", operators = [\"Op\"] }]",
                "[{ module = \"Mine\", operators = [\"Op\"], extra = 3 }]",
                "[{ module = \"Mine\", operators = [\"Op\"] }, { module = \"Mine\", operators = [\"Other\"] }]",
                "[4]");
        var path = directory.resolve("config.toml");
        for (var value : invalid) {
            Files.writeString(path, base.replace("custom_operators = []", "custom_operators = " + value));
            assertThrows(ConfigException.class, () -> TomlConfig.read(path), value);
        }
        Files.writeString(path, base.replace("weights = { name = 8, enum_set = 16 }", "weights = { \"Mine!Op\" = 4 }"));
        assertTrue(assertThrows(ConfigException.class, () -> TomlConfig.read(path)).getMessage().contains("unselected"));
    }
}
