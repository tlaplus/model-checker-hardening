package io.github.tlaplus.hardening.gen;

import static io.github.tlaplus.hardening.gen.TlaIrTestSupport.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaOperators;
import org.junit.jupiter.api.Test;

class IrSpecGeneratorsTest {
    private static final String STEP = "step";
    /** Measured at 0 of 1000 modules when introduced; a required leaf repairs `v' = v`. */
    private static final double STUTTERING_BOUND = 0.05;
    /** Measured at 91 of 505 modules defining an action operator when introduced. */
    private static final double APPLYING_BOUND = 0.12;

    @Test
    void emptyInputProducesASingleVariableModuleWithACompleteAction() {
        var spec = generate(new byte[0]);

        assertEquals(List.of("var0", STEP), variableNames(spec));
        assertEquals(List.of(), spec.operators());
        assertEquals("var0 = FALSE /\\ step = 0", print(spec.initPredicate()));
        assertEquals("(step < 5 /\\ var0' = FALSE /\\ step' = step + 1)", print(spec.nextAction()));
        // Even the empty input yields an invariant over the state rather than a constant: the
        // Boolean terminal rotates over the visible bindings before the closed FALSE.
        assertEquals("var0", print(spec.invariant()));
        assertTrue(spec.property().isEmpty());
        assertEquals(ModuleLimits.DEFAULT_MAXIMUM_STEPS, spec.stepBound());
    }

    @Test
    void generationIsDeterministic() {
        var input = new byte[] {7, 1, 3, 1, 9, 0, 5, 12, 1, 8, 0, 33, 91, 4};

        assertEquals(render(generate(input)), render(generate(input)));
    }

    @Test
    void everyNextDisjunctAccountsForEveryVariableExactlyOnce() {
        forEachGeneratedSpec(spec -> {
            var declared = new LinkedHashSet<>(variableNames(spec));
            var actionOps = actionOperatorBodies(spec);
            for (var disjunct : disjuncts(spec.nextAction())) {
                var account = collectAssignedVars(disjunct, declared, actionOps);
                assertEquals(
                        declared,
                        new LinkedHashSet<>(account),
                        "disjunct does not account for every variable: " + print(disjunct));
                assertEquals(
                        declared.size(),
                        account.size(),
                        "a variable is accounted for twice: " + print(disjunct));
            }
        });
    }

    @Test
    void actionOperatorBodiesAccountForTheirDeclaredEffect() {
        var random = new Random(0xAC7057L);
        var checkedOperators = 0;
        for (var sample = 0; sample < 400; sample++) {
            var input = new byte[256 + random.nextInt(1024)];
            random.nextBytes(input);
            final GeneratedSpec spec;
            try {
                spec = generate(input);
            } catch (InputRejectedException rejected) {
                continue;
            }
            var declared = new LinkedHashSet<>(variableNames(spec));
            var actionOps = actionOperatorBodies(spec);
            for (var operator : actionOperators(spec)) {
                var body = operator.declaration().body();
                var account = collectAssignedVars(body, declared, actionOps);
                assertEquals(
                        new LinkedHashSet<>(operator.effect().variables()),
                        new LinkedHashSet<>(account),
                        operator.declaration().name()
                                + " does not account for its effect: " + print(body));
                assertEquals(
                        operator.effect().variables().size(),
                        account.size(),
                        operator.declaration().name() + " accounts for a variable twice: "
                                + print(body));
                checkedOperators++;
            }
        }
        assertTrue(
                checkedOperators > 20,
                "too few action operators were generated: " + checkedOperators);
    }

    @Test
    void nextMayApplyAnActionOperatorAndStillAccountForEveryVariable() {
        // One variable makes every action operator's effect the full state, so a call matches
        // wherever the shape asks for it; a call still has to be drawn, which needs the bytes a
        // larger input carries into the action.
        var config = IrGenerationConfig.defaults()
                .withModuleLimits(new ModuleLimits(1, 0, new ActionLimits(3, 3, 0, 3), 5, 2));
        var generator = IrGenerators.specs(config);
        var random = new Random(0xACCA11L);
        var sawCall = false;
        var checked = 0;
        for (var sample = 0; sample < 600; sample++) {
            var input = new byte[512 + random.nextInt(1536)];
            random.nextBytes(input);
            final GeneratedSpec spec;
            try {
                spec = generator.generate(input);
            } catch (InputRejectedException rejected) {
                continue;
            }
            var declared = new LinkedHashSet<>(variableNames(spec));
            var actionOps = actionOperatorBodies(spec);
            for (var disjunct : disjuncts(spec.nextAction())) {
                assertEquals(
                        declared,
                        new LinkedHashSet<>(collectAssignedVars(disjunct, declared, actionOps)),
                        "a disjunct applying an action operator does not account for every"
                                + " variable: " + print(disjunct));
                sawCall |= appliesAny(disjunct, actionOps.keySet());
            }
            checked++;
        }
        assertTrue(checked > 100, "too few inputs were admitted to be conclusive: " + checked);
        assertTrue(sawCall, "no generated Next disjunct applied an action operator");
    }

    @Test
    void propertyBasedInputsDecodeToActionsThatChangeState() {
        // Before each module body had its own section, a property-based corpus's invariant used up
        // the median 218-byte input: two thirds of its modules stuttered in every Next disjunct,
        // and Next applied an action operator in one module in a hundred. Lengths here are
        // log-uniform, like the workflow's samples.
        var random = new Random(0x5ec7105L);
        var modules = 0;
        var stuttering = 0;
        var defining = 0;
        var applying = 0;
        for (var sample = 0; sample < 1000; sample++) {
            var bits = 4 + random.nextInt(8);
            var input = new byte[(1 << bits) + random.nextInt(1 << bits)];
            random.nextBytes(input);
            final GeneratedSpec spec;
            try {
                spec = generate(input);
            } catch (InputRejectedException rejected) {
                continue;
            }
            modules++;
            var operators = actionOperatorBodies(spec);
            if (!changesState(spec.nextAction(), operators, STEP)) {
                stuttering++;
            }
            if (!operators.isEmpty()) {
                defining++;
                if (appliesAny(spec.nextAction(), operators.keySet())) {
                    applying++;
                }
            }
        }
        var stutteringShare = (double) stuttering / modules;
        var applyingShare = (double) applying / defining;
        assertTrue(modules > 500, "too few inputs were admitted to be conclusive: " + modules);
        assertTrue(stutteringShare < STUTTERING_BOUND,
                "modules whose Next only stutters: " + stuttering + " of " + modules);
        assertTrue(applyingShare > APPLYING_BOUND,
                "modules applying a defined action operator in Next: " + applying + " of " + defining);
    }

    @Test
    void nestedActionShapesAppearAndRemainComplete() {
        // Larger inputs give the next-state action's section the bytes a nested disjunction or
        // IF-THEN-ELSE needs; a short section decodes to a flat leaf.
        var random = new Random(0xACC0DEL);
        var sawDisjunction = false;
        var sawConditional = false;
        var checked = 0;
        for (var sample = 0; sample < 400; sample++) {
            var input = new byte[512 + random.nextInt(1024)];
            random.nextBytes(input);
            final GeneratedSpec spec;
            try {
                spec = generate(input);
            } catch (InputRejectedException rejected) {
                continue;
            }
            var declared = new LinkedHashSet<>(variableNames(spec));
            var actionOps = actionOperatorBodies(spec);
            for (var disjunct : disjuncts(spec.nextAction())) {
                assertEquals(
                        declared,
                        new LinkedHashSet<>(collectAssignedVars(disjunct, declared, actionOps)),
                        "nested disjunct does not account for every variable: " + print(disjunct));
                sawDisjunction |= containsOperator(disjunct, TlaOperators.OR);
                sawConditional |= containsOperator(disjunct, TlaOperators.IF_THEN_ELSE);
            }
            checked++;
        }
        assertTrue(checked > 100, "too few inputs were admitted to be conclusive: " + checked);
        assertTrue(sawDisjunction, "no generated action nested a disjunction");
        assertTrue(sawConditional, "no generated action used IF-THEN-ELSE");
    }

    @Test
    void everyActionPathAssignsANonStepVariable() {
        forEachGeneratedSpec(spec -> {
            var variables = new LinkedHashSet<>(variableNames(spec));
            variables.remove(STEP);
            var operators = actionOperatorBodies(spec);
            assertTrue(guaranteesAssignment(spec.nextAction(), variables, operators), print(spec.nextAction()));
            actionOperators(spec).forEach(operator -> assertTrue(guaranteesAssignment(
                    operator.declaration().body(), variables, operators), operator.declaration().name()));
        });
    }

    @Test
    void initInvariantAndDefinitionsAreStatePredicatesWithoutPrimes() {
        forEachGeneratedSpec(spec -> {
            assertFalse(containsPrime(spec.initPredicate()), "Init is primed");
            assertFalse(containsPrime(spec.invariant()), "Inv is primed");
            for (var operator : auxiliaryOperators(spec)) {
                assertFalse(
                        containsPrime(operator.body()), operator.name() + " is primed");
            }
        });
    }

    @Test
    void definitionsAreClosedOverTheirParametersAndInitDoesNotReadAVariable() {
        forEachGeneratedSpec(spec -> {
            var variables = Set.copyOf(variableNames(spec));
            // Init constrains the variables and must not read one: a conjunct that did would
            // depend on an evaluation order the predicate does not fix.
            var initReads = new ArrayList<String>();
            collectFreeReads(spec.initPredicate(), variables, initReads, true);
            assertTrue(initReads.isEmpty(), "Init reads " + initReads);

            for (var operator : auxiliaryOperators(spec)) {
                var reads = new ArrayList<String>();
                collectFreeReads(operator.body(), variables, reads, false);
                assertTrue(
                        reads.isEmpty(),
                        operator.name() + " reads state variables " + reads);
            }
        });
    }

    @Test
    void shortAndAdversarialInputsBuildOrRejectCleanly() {
        for (var first = 0; first < 256; first++) {
            assertBuildsOrRejects(new byte[] {(byte) first});
            for (var second = 0; second < 256; second += 17) {
                assertBuildsOrRejects(new byte[] {(byte) first, (byte) second});
            }
        }

        var random = new Random(0x5eedL);
        for (var sample = 0; sample < 2000; sample++) {
            var input = new byte[random.nextInt(256)];
            random.nextBytes(input);
            assertBuildsOrRejects(input);
        }

        var saturated = new byte[8192];
        java.util.Arrays.fill(saturated, (byte) 0xff);
        assertBuildsOrRejects(saturated);
    }

    @Test
    void anEmptyIgnoreListStillBuildsModules() {
        var config = IrGenerationConfig.defaults().withIgnoredCategories(Set.of());

        assertFalse(render(IrGenerators.specs(config).generate(new byte[0])).isEmpty());
    }

    @Test
    void everyCategoryFilterBuildsAdversarialInputsCleanly() {
        var random = new Random(0xca7e60L);
        for (var category : ExpressionCategory.values()) {
            if (!category.isIgnorable()) {
                continue;
            }
            var config = IrGenerationConfig.defaults().ignoring(category);
            for (var sample = 0; sample < 64; sample++) {
                var input = new byte[random.nextInt(129)];
                random.nextBytes(input);
                assertBuildsOrRejects(config, input);
            }
        }
    }

    /** Runs a check over a spread of generated modules, skipping the inputs that reject. */
    private void forEachGeneratedSpec(java.util.function.Consumer<GeneratedSpec> check) {
        var random = new Random(0x0dd1eL);
        var checked = 0;
        for (var sample = 0; sample < 600; sample++) {
            var input = new byte[16 + random.nextInt(240)];
            random.nextBytes(input);
            final GeneratedSpec spec;
            try {
                spec = generate(input);
            } catch (InputRejectedException rejected) {
                continue;
            }
            try {
                check.accept(spec);
            } catch (AssertionError failure) {
                throw new AssertionError(
                        "failed for input " + Base64.getEncoder().encodeToString(input),
                        failure);
            }
            checked++;
        }
        assertTrue(checked > 100, "too few inputs were admitted to be conclusive: " + checked);
    }

    @Test
    void everyDisjunctIsGuardedByTheStepBound() {
        // The guard, and the stuttering disjunct the skeleton adds, close the state graph at the
        // bound: states 0..n are reachable, and both checkers explore exactly those. A length of
        // n transitions covers the same states in Apalache, with no state constraint in TLC.
        for (var steps : List.of(0, 1, 5, 9)) {
            var config = IrGenerationConfig.defaults()
                    .withModuleLimits(new ModuleLimits(
                            1, 0, new ActionLimits(3, 3, 0, 3), steps, 2));
            var spec = IrGenerators.specs(config).generate(new byte[0]);

            assertEquals(steps, spec.stepBound());
            for (var disjunct : disjuncts(spec.nextAction())) {
                assertTrue(print(disjunct).replaceFirst("^\\(", "").startsWith(STEP + " < " + steps + " /\\ "),
                        "disjunct is not guarded by step bound " + steps + ": " + print(disjunct));
            }
        }
    }

    @Test
    void enabledActionAndTemporalCategoriesKeepLevelsAndAccounting() {
        // With the categories enabled, post-assignment guards may prime and the property is
        // temporal, while every other body stays a state predicate and every disjunct's spine still
        // accounts for every variable exactly once.
        var config = IrGenerationConfig.defaults()
                .withIgnoredCategories(Set.of(ExpressionCategory.UNBOUND, ExpressionCategory.EXOTIC));
        var generator = IrGenerators.specs(config);
        var random = new Random(0x7e3905L);
        var modules = 0;
        var primedGuards = 0;
        var properties = 0;
        var fairness = 0;
        var constraints = 0;
        for (var sample = 0; sample < 600; sample++) {
            var input = new byte[128 + random.nextInt(1024)];
            random.nextBytes(input);
            final GeneratedSpec spec;
            try {
                spec = generator.generate(input);
            } catch (InputRejectedException rejected) {
                continue;
            }
            try {
                assertEquals(IrLevel.STATE, level(spec.initPredicate()));
                assertEquals(IrLevel.STATE, level(spec.invariant()));
                auxiliaryOperators(spec).forEach(operator -> assertEquals(IrLevel.STATE, level(operator.body())));
                var declared = new LinkedHashSet<>(variableNames(spec));
                var actionOps = actionOperatorBodies(spec);
                for (var disjunct : disjuncts(spec.nextAction())) {
                    var account = collectAssignedVars(accountedSpine(disjunct, STEP), declared, actionOps);
                    assertEquals(declared, new LinkedHashSet<>(account), print(disjunct));
                    assertEquals(declared.size(), account.size(), print(disjunct));
                    for (var guard : postAssignmentGuards(disjunct, STEP)) {
                        assertFalse(level(guard) == IrLevel.TEMPORAL, print(guard));
                        primedGuards += containsPrime(guard) ? 1 : 0;
                    }
                }
                if (spec.property().isPresent()) {
                    var property = spec.property().get();
                    properties++;
                    fairness += property.fairness().size();
                    constraints += property.actionConstraints().size();
                    assertCheckableTemporal(property.formula(), true);
                    property.fairness().forEach(condition -> assertEquals(IrLevel.TEMPORAL, level(condition)));
                    property.actionConstraints().forEach(constraint -> assertEquals(IrLevel.TEMPORAL, level(constraint)));
                }
            } catch (AssertionError failure) {
                throw new AssertionError(
                        "failed for input " + Base64.getEncoder().encodeToString(input), failure);
            }
            modules++;
        }
        assertTrue(modules > 300, "too few inputs were admitted to be conclusive: " + modules);
        assertTrue(primedGuards > 0, "no post-assignment guard read a primed variable");
        assertTrue(properties > modules / 4, "modules with a property: " + properties + " of " + modules);
        assertTrue(fairness > 0, "no property had fairness");
        assertTrue(constraints > 0, "no property had an action constraint");
    }

    /** Every declaration is its own label scope: a module has no binder in scope at its top. */
    private void assertLabelParameters(GeneratedSpec spec) {
        spec.operators().stream().map(GeneratedOperator::declaration)
                .forEach(operator -> TlaIrTestSupport.assertLabelParameters(operator.body()));
        spec.generated().forEach(TlaIrTestSupport::assertLabelParameters);
    }

    private GeneratedSpec generate(byte[] input) {
        return IrGenerators.specs(IrGenerationConfig.defaults()).generate(input);
    }

    private void assertBuildsOrRejects(byte[] input) {
        assertBuildsOrRejects(IrGenerationConfig.defaults(), input);
    }

    private void assertBuildsOrRejects(IrGenerationConfig config, byte[] input) {
        try {
            var spec = IrGenerators.specs(config).generate(input);
            assertFalse(render(spec).isEmpty());
            assertLabelParameters(spec);
        } catch (InputRejectedException expected) {
            // Some forms intentionally reject when the current scope cannot satisfy them.
        } catch (RuntimeException failure) {
            throw new AssertionError(
                    "generation failed for input " + Base64.getEncoder().encodeToString(input),
                    failure);
        }
    }

    private String render(GeneratedSpec spec) {
        var text = new StringBuilder();
        spec.variables().forEach(variable -> text.append(variable.name()).append('\n'));
        spec.operators().stream().map(GeneratedOperator::declaration)
                .forEach(operator ->
                        text.append(operator.name())
                                .append(" == ")
                                .append(print(operator.body()))
                                .append('\n'));
        spec.generated().forEach(expression -> text.append(print(expression)).append('\n'));
        return text.toString();
    }

}
