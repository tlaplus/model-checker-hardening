package io.github.tlaplus.hardening.config;

import io.github.tlaplus.hardening.gen.engine.CustomExpressionKind;
import io.github.tlaplus.hardening.gen.library.LibraryLinkage;
import io.github.tlaplus.hardening.gen.library.ModuleLink;
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
                "[{ module = \"Mine\", operators = [\"Op\"], link = \"extends\" }]",
                "[{ module = \"Mine\", operators = [\"Op\"], link = 1 }]",
                "[{ module = \"Mine\", operators = [\"Op\"], link = \"diff\" }]",
                "[{ module = \"Mine\", operators = [\"Op\"], link = \"diff\", tlc_module = \"Mine\" }]",
                "[{ module = \"Mine\", operators = [\"Op\"], link = \"diff\", tlc_module = 1 }]",
                "[{ module = \"Mine\", operators = [\"Op\"], link = \"diff\", tlc_module = \"../T\" }]",
                "[{ module = \"Mine\", operators = [\"Op\"], link = \"instance\", tlc_module = \"T\" }]",
                "[{ module = \"Mine\", operators = [\"Op\"], tlc_module = \"T\" }]",
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

    @Test
    void roundTripsInstanceLinkageAndOmitsTheDefault(@TempDir Path directory) throws Exception {
        var path = directory.resolve("config.toml");
        Files.writeString(path, TomlConfig.render(FuzzTlaConfig.defaults()).replace("custom_operators = []",
                "custom_operators = [{ module = \"A\", operators = [\"X\"], link = \"instance\" },"
                        + " { module = \"B\", operators = [\"Y\"], link = \"inline\" }]"));
        var config = TomlConfig.read(path);
        assertEquals(List.of(LibraryLinkage.INSTANCE, LibraryLinkage.INLINE),
                config.libraries().modules().stream().map(OperatorLibraryConfig.Module::linkage).toList());
        var rendered = TomlConfig.render(config);
        assertTrue(rendered.contains("{ module = \"A\", operators = [\"X\"], link = \"instance\" },"
                + " { module = \"B\", operators = [\"Y\"] }"), rendered);
        assertTrue(config.libraries().hasSourceAliases());
        assertEquals(config.libraries().classpath(), config.libraries().sourceCheckerClasspath());
    }

    @Test
    void roundTripsDiffLinkageWithItsTlcModule(@TempDir Path directory) throws Exception {
        var path = directory.resolve("config.toml");
        var selection = "{ module = \"A\", operators = [\"X\"], link = \"diff\", tlc_module = \"ATLC\" }";
        Files.writeString(path, TomlConfig.render(FuzzTlaConfig.defaults())
                .replace("custom_operators = []", "custom_operators = [" + selection + "]"));
        var config = TomlConfig.read(path);
        var module = config.libraries().modules().getFirst();
        assertEquals(new ModuleLink(LibraryLinkage.DIFF, "ATLC"), module.link());
        var rendered = TomlConfig.render(config);
        assertTrue(rendered.contains(selection), rendered);
        assertTrue(config.libraries().hasSourceAliases());
        assertEquals(config.libraries().classpath(), config.libraries().sourceCheckerClasspath());
    }

    @Test
    void onlyDiffLinkageNamesASeparateTlcModule() {
        assertThrows(IllegalArgumentException.class, () -> ModuleLink.of("A", LibraryLinkage.DIFF));
        assertThrows(IllegalArgumentException.class,
                () -> new OperatorLibraryConfig.Module("A", List.of("X"), new ModuleLink(LibraryLinkage.DIFF, "A")));
        assertThrows(IllegalArgumentException.class,
                () -> new OperatorLibraryConfig.Module("A", List.of("X"), new ModuleLink(LibraryLinkage.INSTANCE, "B")));
        assertEquals("A", new OperatorLibraryConfig.Module("A", List.of("X"), LibraryLinkage.INSTANCE).link().sourceModule());
    }

    @Test
    void checkersReadTheClasspathOnlyForInstanceLinkage() {
        var classpath = List.of(Path.of("/lib.jar"));
        var inline = new OperatorLibraryConfig(classpath, List.of(new OperatorLibraryConfig.Module("A", List.of("X"))));
        assertEquals(List.of(), inline.sourceCheckerClasspath());
    }

    @Test
    void linkageNamesArePartOfTheConfigurationAndManifestFormat() {
        // Stored in config.toml and in .operator-library; renaming one breaks existing corpora.
        assertEquals(List.of("inline", "instance", "diff"),
                java.util.Arrays.stream(LibraryLinkage.values()).map(LibraryLinkage::encodedName).toList());
    }

    @Test
    void readsALibraryFileRelativeToItsDirectory(@TempDir Path directory) throws Exception {
        var path = directory.resolve("lib.toml");
        Files.writeString(path, """
                [generator]
                classpath = ["jars/Mods.jar"]
                custom_operators = [{ module = "A", operators = ["X"], link = "instance" }]
                """);
        var library = TomlConfig.readLibrary(path);
        assertEquals(List.of(directory.resolve("jars/Mods.jar")), library.classpath());
        assertEquals(LibraryLinkage.INSTANCE, library.modules().getFirst().linkage());
        for (var invalid : List.of(
                "[generator]\nclasspath = []\n",
                "[generator]\nclasspath = []\ncustom_operators = []\nweights = {}\n",
                "[generator]\nclasspath = []\ncustom_operators = []\n[pbt]\n")) {
            Files.writeString(path, invalid);
            assertThrows(ConfigException.class, () -> TomlConfig.readLibrary(path), invalid);
        }
    }
}
