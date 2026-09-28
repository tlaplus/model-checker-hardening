package io.github.tlaplus.hardening.workflow.library;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.config.CheckerProfile;
import io.github.tlaplus.hardening.config.MetamorphicConfig;
import io.github.tlaplus.hardening.gen.rewrite.RewriteRule;
import io.github.tlaplus.hardening.gen.rewrite.RuleParameter;
import io.github.tlaplus.hardening.workflow.WorkflowException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Prepares rule modules with the real SANY and Snowcat (ADR 0017 probes P1 and P2). */
class RuleLibraryPreparationTest {
    private static final Path SHIPPED = Path.of("libraries/rewrites").toAbsolutePath();

    @Test
    void preparesTheShippedRulesInDeclarationOrder() throws Exception {
        var prepared = prepare(config(SHIPPED, "Rewrites", Map.of("AddSub", 3)));
        var rules = prepared.library().rules();
        assertEquals(List.of("PlusZero", "AddSub", "DoubleNeg", "UnionSelf", "UnchangedPrime", "ForallNotExists"),
                rules.stream().map(RewriteRule::name).toList());
        assertEquals(3, rules.get(1).weight());
        assertEquals(RuleParameter.Kind.FRESH, rules.get(1).parameter("y").orElseThrow().kind());
        assertEquals(RuleParameter.Kind.HIGHER_ORDER, rules.get(5).parameter("P").orElseThrow().kind());
        assertTrue(prepared.manifest().startsWith("fuzztla-rules-v1\napalache "), prepared.manifest());
        assertTrue(prepared.manifest().contains("\nsource Rewrites.tla "), prepared.manifest());
        assertTrue(prepared.manifest().endsWith("module Rewrites\nweight AddSub 3\n"), prepared.manifest());
    }

    @Test
    void helpersOfAnExtendedModuleAreNotRules(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("Helpers.tla"), "---- MODULE Helpers ----\nEXTENDS Integers\nZero == 0\n====\n");
        Files.writeString(directory.resolve("Rules.tla"),
                "---- MODULE Rules ----\nEXTENDS Helpers\nPlusHelper(x) == x = x + Zero\n====\n");
        var rules = prepare(config(directory, "Rules", Map.of())).library().rules();
        assertEquals(List.of("PlusHelper"), rules.stream().map(RewriteRule::name).toList());
    }

    @Test
    void reportsARuleThatBreaksTheContract(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("Rules.tla"),
                "---- MODULE Rules ----\nEXTENDS Integers\nNotARule(x) == x + 0\n====\n");
        var failure = assertThrows(WorkflowException.class, () -> prepare(config(directory, "Rules", Map.of())));
        assertTrue(failure.getMessage().contains("rewrite rule NotARule: the body must be A = B or A <=> B"),
                failure.getMessage());
    }

    @Test
    void preparesNothingWithoutARuleModule() throws Exception {
        var prepared = prepare(MetamorphicConfig.defaults());
        assertTrue(prepared.library().isEmpty());
        assertEquals("", prepared.manifest());
    }

    private static RuleLibraryPreparation.Prepared prepare(MetamorphicConfig config) throws WorkflowException {
        return RuleLibraryPreparation.prepare(config, CheckerProfile.APALACHE.defaults());
    }

    private static MetamorphicConfig config(Path classpath, String module, Map<String, Integer> weights) {
        return new MetamorphicConfig(Optional.of(new MetamorphicConfig.RuleModule(module, List.of(classpath))), weights);
    }
}
