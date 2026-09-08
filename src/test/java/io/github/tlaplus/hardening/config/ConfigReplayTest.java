package io.github.tlaplus.hardening.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Cross-revision rendering and validation-precedence fixtures recorded with a3218a2. */
public class ConfigReplayTest {
    @Test
    void preservesNondefaultValuesAndTheFirstDiagnostic(@TempDir Path directory) throws Exception {
        try (var fixture = getClass().getResourceAsStream("/config/replay.txt")) {
            assertEquals(new String(fixture.readAllBytes(), StandardCharsets.UTF_8), replay(directory));
        }
    }

    public static void main(String[] arguments) throws Exception {
        var directory = Files.createTempDirectory("config-replay-");
        try {
            Files.writeString(Path.of(arguments[0]), replay(directory));
        } finally {
            Files.deleteIfExists(directory.resolve("config.toml"));
            Files.delete(directory);
        }
    }

    private static String replay(Path directory) throws Exception {
        // Make this fixture independent of the host's CPU count. default.toml.template separately
        // pins default rendering and WorkflowConfigTest pins the actual host-dependent formula.
        var base = TomlConfig.render(FuzzTlaConfig.defaults()).replaceAll("(?m)^workers = \\d+$", "workers = 3");
        var cases = new LinkedHashMap<String, String>();
        cases.put("valid", base);
        cases.put("nondefault", base.replace("max_nodes = 128", "max_nodes = 71")
                .replace("max_type_depth = 3", "max_type_depth = 2")
                .replace("kind = \"expr\"", "kind = \"module\"")
                .replace("max_action_depth = 3", "max_action_depth = 1")
                .replace("max_steps = 5", "max_steps = 2")
                .replace("weights = { name = 8, enum_set = 16 }", "weights = { plus = 9, name = 2 }")
                .replace("richness_cohorts = 10", "richness_cohorts = 3")
                .replace("richness_nesting_base = 2.0", "richness_nesting_base = 1.5"));
        cases.put("missing-before-unknown", base.replace("max_nodes = 128\n", "unexpected = 1\n")
                .replace("richness_cohorts = 10\n", ""));
        cases.put("table-before-key", base.replace("[workflow.tlc]", "[workflow.wrong]")
                .replace("max_nodes = 128\n", ""));
        cases.put("kind-before-limits", base.replace("kind = \"expr\"", "kind = \"invalid\"")
                .replace("max_nodes = 128", "max_nodes = 0"));
        cases.put("limit-read-order", base.replace("max_type_depth = 3", "max_type_depth = \"wrong\"")
                .replace("max_nodes = 128", "max_nodes = \"wrong\""));
        cases.put("record-order", base.replace("max_type_depth = 3", "max_type_depth = -1")
                .replace("max_nodes = 128", "max_nodes = 0"));
        cases.put("checker-before-pbt", base.replace("timeout_sec = 30", "timeout_sec = 0")
                .replace("richness_cohorts = 10", "richness_cohorts = 0"));
        cases.put("pbt-order", base.replace("richness_nesting_base = 2.0", "richness_nesting_base = nan")
                .replace("richness_threshold_base = 1.5", "richness_threshold_base = 1.0"));
        cases.put("classpath", base.replace("classpath = []", "classpath = [\"sources\", \"lib.jar\"]"));
        var output = new StringBuilder();
        for (var entry : cases.entrySet()) {
            Files.writeString(directory.resolve("config.toml"), entry.getValue());
            output.append(entry.getKey()).append('\n');
            try {
                var config = TomlConfig.read(directory.resolve("config.toml"));
                output.append(TomlConfig.render(config).lines()
                        .filter(line -> !line.startsWith("#") && !line.isBlank())
                        .collect(Collectors.joining("\n"))
                        .replace(directory.toAbsolutePath().toString(), "<root>"));
            } catch (ConfigException exception) {
                output.append(exception.getClass().getName()).append(": ").append(exception.getMessage());
            }
            output.append('\n');
        }
        return output.toString();
    }
}
