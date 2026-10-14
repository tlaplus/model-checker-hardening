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
        assertEquals(List.of("PlusZero", "AddSub", "DoubleNeg", "UnionSelf", "UnchangedPrime", "ForallNotExists",
                "TimesOne", "NegNeg", "PlusComm", "TimesComm", "PlusAssoc", "MinusAsPlusNeg", "TimesDistrib",
                "LtAsNotGe", "LeAsLtOrEq", "GtAsLt", "GeAsLe", "NegAsMinus", "DoubleAsTimes", "MinusMinus",
                "DivModPos", "DivSplit", "DivSplitTwo", "DivModTwo", "DivModThree", "ModTwoFloor", "ModInRange",
                "AndTrue", "OrFalse", "AndComm", "OrComm", "DeMorganAnd", "DeMorganOr", "ImpliesAsOr",
                "EquivAsImplies", "AndAssoc", "OrAssoc", "IfAsAndOr", "EquivAsEq", "BoolAsIf", "ImpliesCurry",
                "ExistsBool", "NeqAsNotEq", "EqSym", "IfNegate", "IfTrue", "IfAsCase", "IfAsCaseNeg", "IfLiftEq",
                "EqAsIn", "UnionEmpty", "IntersectSelf", "MinusEmpty", "UnionComm", "IntersectComm", "MinusAsFilter",
                "IntersectAsFilter", "InUnion", "InIntersect", "NotInAsNotIn", "SubsetAsForall", "MapIdentity",
                "FilterTrue", "FilterFilter", "ExistsNotForall", "ExistsUnion", "ForallAnd", "ExistsAsFilter",
                "ForallAsFilter", "ExistsOr", "ForallSwap", "ExistsSwap", "ForallPair", "ExistsPair", "ExistsFilter",
                "ForallFilter", "ExistsMap", "MapMap", "MapAsUnion", "FilterAsUnion", "InFilter", "InMap",
                "InAsExists", "InRange", "UnionAsBigUnion", "SubsetAsPowerset", "InPowersetAsSubset",
                "SetEqAsSubset", "FunEta", "DomainOfCtor", "ExceptTwice", "InFunSet", "ExceptAsCtor", "DomainExcept",
                "ConcatEmpty", "EmptyConcat", "ConcatAssoc", "AppendAsConcat", "LenConcat", "LenAppend",
                "SubSeqWhole", "TailCons", "InSeqSet", "PairEta", "PairEqAsAnd", "PairInProduct", "RecordExceptSame",
                "RecordExceptTwice", "RecordExceptGet", "VariantTagOf", "VariantGetOf", "LetName", "LetApply",
                "LetConst", "LetHigher", "ExistsViaLet", "ForallViaLet", "FilterViaLet", "MapViaLet", "FunViaLet",
                "FilterViaLambda", "ExistsViaLambda", "FunBeta", "RecordProj", "TupleProj", "SeqHead",
                "FunApplyCtor", "FunApplyConst", "ChooseSingleton", "CaseTrue"),
                rules.stream().map(RewriteRule::name).toList());
        var weights = new java.util.HashMap<String, Integer>();
        rules.forEach(rule -> weights.put(rule.name(), rule.weight()));
        assertEquals(3, weights.get("AddSub"), "the configuration overrides the default weight");
        assertEquals(1, weights.get("AndTrue"));
        assertEquals(2, weights.get("OrComm"));
        assertEquals(4, weights.get("FunEta"));
        assertEquals(8, weights.get("IntersectAsFilter"));
        assertEquals(8, weights.get("RecordExceptTwice"), "a weight annotation follows a type annotation");
        assertEquals(8, weights.get("ExistsViaLet"));
        assertEquals(1, weights.get("LetApply"), "a redex that matches every value pads");
        assertEquals(RuleParameter.Kind.FRESH, rules.get(1).parameter("y").orElseThrow().kind());
        assertEquals(RuleParameter.Kind.HIGHER_ORDER, rules.get(5).parameter("P").orElseThrow().kind());
        assertTrue(prepared.manifest().startsWith("fuzztla-rules-v1\napalache "), prepared.manifest());
        assertTrue(prepared.manifest().contains("\nsource Rewrites.tla "), prepared.manifest());
        assertTrue(prepared.manifest().contains(
                "\nmodule Rewrites\nweight AddSub 3\nweight UnchangedPrime 8\nweight ForallNotExists 8\n"),
                prepared.manifest());
        assertTrue(prepared.manifest().endsWith("\nweight FilterViaLambda 8\nweight ExistsViaLambda 8\nweight FunBeta 8\n"),
                prepared.manifest());
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
    void theConfigurationOverridesAnAnnotatedWeight(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("Rules.tla"), """
                ---- MODULE Rules ----
                EXTENDS Integers
                \\* @weight: 5;
                PlusZero(x) == x = x + 0
                \\* @weight: 7;
                TimesOne(x) == x = x * 1
                NegNeg(x) == x = -(-x)
                ====
                """);
        var prepared = prepare(config(directory, "Rules", Map.of("TimesOne", 1, "NegNeg", 0)));
        assertEquals(List.of(5, 1, 0), prepared.library().rules().stream().map(RewriteRule::weight).toList());
        assertTrue(prepared.manifest().endsWith("module Rules\nweight PlusZero 5\nweight TimesOne 1\nweight NegNeg 0\n"),
                prepared.manifest());
    }

    @Test
    void rejectsAWeightOutOfRange(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("Rules.tla"),
                "---- MODULE Rules ----\nEXTENDS Integers\n\\* @weight: 65;\nPlusZero(x) == x = x + 0\n====\n");
        var failure = assertThrows(WorkflowException.class, () -> prepare(config(directory, "Rules", Map.of())));
        assertTrue(failure.getMessage().contains("weight of rewrite rule PlusZero must be in the range 0..64"),
                failure.getMessage());
    }

    @Test
    void rejectsAMalformedWeight(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("Rules.tla"),
                "---- MODULE Rules ----\nEXTENDS Integers\n(* @weight: high; *)\nPlusZero(x) == x = x + 0\n====\n");
        var failure = assertThrows(WorkflowException.class, () -> prepare(config(directory, "Rules", Map.of())));
        assertTrue(failure.getMessage().contains("rewrite rule PlusZero: malformed annotation"), failure.getMessage());
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
        return new MetamorphicConfig(Optional.of(new MetamorphicConfig.RuleModule(module, List.of(classpath))), weights,
                io.github.tlaplus.hardening.gen.rewrite.RewriteLimits.defaults(), MetamorphicConfig.Adoption.defaults());
    }
}
