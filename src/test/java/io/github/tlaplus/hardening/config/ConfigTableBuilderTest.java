package io.github.tlaplus.hardening.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigTableBuilderTest {
    @Test
    void projectionsRegisterIntoOneOrderedTableAndBuildFreezesItsKeys() {
        var table = new ConfigTableBuilder<>("example", FuzzTlaConfig::generator);
        var expressions = table.project(config -> config.expressions());
        var modules = table.project(config -> config.modules());
        var nodes = expressions.integer("nodes", limits -> limits.maximumNodes(), "node limit");
        var variables = modules.integer("variables", limits -> limits.maximumVariables());
        var frozen = table.build();
        var steps = modules.integer("steps", limits -> limits.maximumSteps());

        assertEquals(List.of(nodes, variables), frozen.keys());
        assertEquals(List.of(nodes, variables, steps), table.build().keys());
        assertEquals(List.of("[example]", "# node limit", "nodes = 128", "variables = 3"),
                frozen.render(FuzzTlaConfig.defaults()));
        assertThrows(UnsupportedOperationException.class, () -> frozen.keys().clear());
    }
}
