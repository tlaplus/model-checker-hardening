package io.github.tlaplus.hardening.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.ExpressionCategory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TomlConfigTest {
    @Test
    void renderedDefaultsRoundTripThroughTheStrictParser(@TempDir Path directory) throws Exception {
        var path = directory.resolve("config.toml");
        TomlConfig.writeNew(path, FuzzTlaConfig.defaults());

        assertEquals(FuzzTlaConfig.defaults(), TomlConfig.read(path));
        assertTrue(Files.readString(path).contains("max_entries = 1000"));
        assertTrue(Files.readString(path).contains("timeout_sec = 30"));
        assertTrue(Files.readString(path).contains("max_nodes = 32"));
        assertTrue(Files.readString(path)
                .contains("ignore = [\"action\", \"temporal\", \"unbound\", \"exotic\"]"));
        assertTrue(Files.readString(path).contains("max_input_bytes = 10240"));
        assertTrue(Files.readString(path).contains("max_heap_mb = 512"));
        assertTrue(Files.readString(path).contains("workers = 1"));
        assertTrue(Files.readString(path)
                .contains("[workflow.apalache]\n"
                        + "# Maximum combined occupancy of the Apalache result directories.\n"
                        + "max_entries = 1000\n"
                        + "# Wall-clock limit for checking one generated specification.\n"
                        + "timeout_sec = 30\n"
                        + "# Maximum heap allocated to each persistent Apalache worker JVM.\n"
                        + "max_heap_mb = 1024\n"
                        + "# Number of concurrent FuzzTLA Apalache workers.\n"
                        + "# Initialized to half the available processors, rounded down (at least one).\n"
                        + "workers = "
                        + CheckerStageConfig.DEFAULT_APALACHE_WORKERS));
        assertTrue(Files.readString(path).contains("richness_cohorts = 10"));
        assertTrue(Files.readString(path).contains("richness_nesting_base = 2.0"));
        assertTrue(Files.readString(path).contains("richness_threshold_base = 1.5"));
    }

    /**
     * Pins the three views of a key together: a key declared in the schema must be rendered, and
     * dropping its rendered line must be rejected as a missing key. A key that drifts out of
     * validation, out of reading, or out of rendering fails here.
     */
    @Test
    void everyDeclaredKeyIsRenderedAndRequired(@TempDir Path directory) throws Exception {
        var blocks = new ArrayList<>(
                List.of(TomlConfig.render(FuzzTlaConfig.defaults()).split("\n\n")));
        assertEquals(ConfigSchema.TABLES.size(), blocks.size());

        for (var index = 0; index < ConfigSchema.TABLES.size(); index++) {
            var table = ConfigSchema.TABLES.get(index);
            var block = blocks.get(index);
            assertTrue(
                    block.startsWith("[" + table.path() + "]\n"),
                    "block " + index + " is not table " + table.path());

            for (var key : table.keys()) {
                var assignment = block.lines()
                        .filter(line -> line.startsWith(key.name() + " = "))
                        .toList();
                assertEquals(
                        1, assignment.size(), key.path() + " is not rendered exactly once");

                var withoutKey = new ArrayList<>(blocks);
                withoutKey.set(
                        index,
                        block.lines()
                                .filter(line -> !line.equals(assignment.get(0)))
                                .collect(Collectors.joining("\n")));
                var failure =
                        assertInvalid(directory, String.join("\n\n", withoutKey));
                assertTrue(
                        failure.getMessage()
                                .contains("missing " + table.path() + " keys: " + key.name()),
                        "dropping " + key.path() + " reported: " + failure.getMessage());
            }
        }
    }

    @Test
    void readsEmptyAndPartialIgnoreLists(@TempDir Path directory) throws Exception {
        var rendered = TomlConfig.render(FuzzTlaConfig.defaults());

        var empty = readConfig(
                directory,
                rendered.replace(
                        "ignore = [\"action\", \"temporal\", \"unbound\", \"exotic\"]",
                        "ignore = []"));
        var partial = readConfig(
                directory,
                rendered.replace(
                        "ignore = [\"action\", \"temporal\", \"unbound\", \"exotic\"]",
                        "ignore = [\"quantifier\", \"bool_logic\", \"finite_set\", \"label\"]"));

        assertEquals(Set.of(), empty.generator().ignoredCategories());
        assertEquals(
                Set.of(
                        ExpressionCategory.QUANTIFIER,
                        ExpressionCategory.BOOL_LOGIC,
                        ExpressionCategory.FINITE_SET,
                        ExpressionCategory.LABEL),
                partial.generator().ignoredCategories());
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
                        .replace("max_nodes = 32\n", ""));
        assertTrue(missing.getMessage().contains("missing generator keys: max_nodes"));

        var missingIgnore = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace(
                                "ignore = [\"action\", \"temporal\", \"unbound\", \"exotic\"]\n",
                                ""));
        assertTrue(missingIgnore.getMessage().contains("missing generator keys: ignore"));

        var missingRichness = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace("richness_cohorts = 10\n", ""));
        assertTrue(missingRichness.getMessage().contains("missing pbt keys: richness_cohorts"));

        var unknown = assertInvalid(
                directory,
                        TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace("[workflow]", "unexpected = 1\n\n[workflow]"));
        assertTrue(unknown.getMessage().contains("unknown generator keys: unexpected"));
    }

    @Test
    void rejectsMissingTablesAndUnexpectedRootKeys(@TempDir Path directory) throws Exception {
        var missing = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace("[pbt]", "[not_pbt]"));
        assertTrue(missing.getMessage().contains("missing root keys: pbt"));

        var current = TomlConfig.render(FuzzTlaConfig.defaults());
        var tlcStart = current.indexOf("[workflow.tlc]");
        var pbtStart = current.indexOf("[pbt]");
        var missingTlc = assertInvalid(
                directory,
                current.substring(0, tlcStart) + current.substring(pbtStart));
        assertTrue(missingTlc.getMessage().contains("expected 'tlc' to be a table"));

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
                        .replace(
                                "# Maximum number of unique entries across every workflow directory.\nmax_entries = 1000",
                                "# Maximum number of unique entries across every workflow directory.\nmax_entries = \"many\""));
        assertTrue(wrongType.getMessage().contains("workflow.max_entries"));

        var tooLarge = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace("max_input_bytes = 10240", "max_input_bytes = 2147483648"));
        assertTrue(tooLarge.getMessage().contains("outside the supported integer range"));

        var wrongRichnessType = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace("richness_nesting_base = 2.0", "richness_nesting_base = \"two\""));
        assertTrue(wrongRichnessType.getMessage().contains("pbt.richness_nesting_base"));

        var wrongIgnoreShape = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace(
                                "ignore = [\"action\", \"temporal\", \"unbound\", \"exotic\"]",
                                "ignore = \"action\""));
        assertTrue(wrongIgnoreShape.getMessage().contains("generator.ignore"));

        var wrongIgnoreElement = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace(
                                "ignore = [\"action\", \"temporal\", \"unbound\", \"exotic\"]",
                                "ignore = [\"action\", 1]"));
        assertTrue(wrongIgnoreElement.getMessage().contains("generator.ignore[1]"));

        var unknownCategory = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace(
                                "ignore = [\"action\", \"temporal\", \"unbound\", \"exotic\"]",
                                "ignore = [\"state\"]"));
        assertTrue(unknownCategory.getMessage().contains("unknown expression category 'state'"));
    }

    @Test
    void reportsRecordValidationFailuresAsConfigurationErrors(@TempDir Path directory)
            throws Exception {
        var invalidGenerator = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace("max_nodes = 32", "max_nodes = 0"));
        assertTrue(invalidGenerator.getMessage().contains("maximumNodes must be positive"));

        var impossibleCorpus = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace("max_input_bytes = 10240", "max_input_bytes = 0"));
        assertTrue(impossibleCorpus.getMessage().contains("distinct bounded inputs"));

        var invalidCohorts = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace("richness_cohorts = 10", "richness_cohorts = 0"));
        assertTrue(invalidCohorts.getMessage().contains("richnessCohorts must be positive"));

        var ignoredCore = assertInvalid(
                directory,
                TomlConfig.render(FuzzTlaConfig.defaults())
                        .replace(
                                "ignore = [\"action\", \"temporal\", \"unbound\", \"exotic\"]",
                                "ignore = [\"core\"]"));
        assertTrue(ignoredCore.getMessage().contains("core expression category cannot be ignored"));
    }

    private ConfigException assertInvalid(Path directory, String contents) throws Exception {
        var path = directory.resolve("config-" + System.nanoTime() + ".toml");
        Files.writeString(path, contents);
        return assertThrows(ConfigException.class, () -> TomlConfig.read(path));
    }

    private FuzzTlaConfig readConfig(Path directory, String contents) throws Exception {
        var path = directory.resolve("config-" + System.nanoTime() + ".toml");
        Files.writeString(path, contents);
        return TomlConfig.read(path);
    }
}
