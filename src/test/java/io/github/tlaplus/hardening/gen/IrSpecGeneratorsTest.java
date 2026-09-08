package io.github.tlaplus.hardening.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.io.lir.PrettyWriter;
import at.forsyte.apalache.io.lir.TextLayout;
import at.forsyte.apalache.io.lir.TlaDeclAnnotator;
import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.TlaVarDecl;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import scala.jdk.javaapi.CollectionConverters;

class IrSpecGeneratorsTest {
    private static final String STEP = "step";

    @Test
    void emptyInputProducesASingleVariableModuleWithACompleteAction() {
        var spec = generate(new byte[0]);

        assertEquals(List.of("var0", STEP), variableNames(spec));
        assertEquals(List.of(), spec.auxiliaryOperators());
        assertEquals("var0 = FALSE /\\ step = 0", print(spec.initPredicate()));
        assertEquals("(var0' = FALSE /\\ step' = step + 1)", print(spec.nextAction()));
        // Even the empty input yields an invariant over the state rather than a constant: the
        // Boolean terminal rotates over the visible bindings before the closed FALSE.
        assertEquals("var0", print(spec.invariant()));
        assertEquals("step <= 5", print(spec.boundPredicate()));
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
            for (var operator : spec.actionOperators()) {
                var body = operator.declaration().body();
                var account = collectAssignedVars(body, declared, actionOps);
                assertEquals(
                        new LinkedHashSet<>(operator.effect()),
                        new LinkedHashSet<>(account),
                        operator.declaration().name()
                                + " does not account for its effect: " + print(body));
                assertEquals(
                        operator.effect().size(),
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
                .withModuleLimits(new ModuleLimits(1, 0, 3, 3, 0, 3, 5));
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
    void nestedActionShapesAppearAndRemainComplete() {
        // The next-state action is drawn after the invariant, the auxiliary operators, and Init, so
        // a short input is exhausted before the action shape and always decodes to a flat leaf.
        // Larger inputs leave the bytes a nested disjunction or IF-THEN-ELSE needs.
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
                sawDisjunction |= containsOperator(disjunct, "OR");
                sawConditional |= containsOperator(disjunct, "IF_THEN_ELSE");
            }
            checked++;
        }
        assertTrue(checked > 100, "too few inputs were admitted to be conclusive: " + checked);
        assertTrue(sawDisjunction, "no generated action nested a disjunction");
        assertTrue(sawConditional, "no generated action used IF-THEN-ELSE");
    }

    @Test
    void initInvariantAndDefinitionsAreStatePredicatesWithoutPrimes() {
        forEachGeneratedSpec(spec -> {
            assertFalse(containsPrime(spec.initPredicate()), "Init is primed");
            assertFalse(containsPrime(spec.invariant()), "Inv is primed");
            assertFalse(containsPrime(spec.boundPredicate()), "Bound is primed");
            for (var operator : spec.auxiliaryOperators()) {
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

            for (var operator : spec.auxiliaryOperators()) {
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

    /** Returns the top-level disjuncts of the next-state action. */
    private List<TlaEx> disjuncts(TlaEx nextAction) {
        if (nextAction instanceof OperEx operator && operator.oper().name().equals("OR")) {
            return CollectionConverters.asJava(operator.args()).stream().toList();
        }
        return List.of(nextAction);
    }

    /** Maps each generated action operator's name to its body. */
    private Map<String, TlaEx> actionOperatorBodies(GeneratedSpec spec) {
        return spec.actionOperators().stream()
                .collect(Collectors.toMap(
                        operator -> operator.declaration().name(),
                        operator -> operator.declaration().body()));
    }

    /**
     * Collects the variables one action shape settles, recursively: those given a next value by a
     * primed equality or membership, and those listed in an {@code UNCHANGED}. The conjunctive
     * spine unions its children; every arm of a nested disjunction and every branch of an
     * IF-THEN-ELSE must settle the same variables, so the shape collapses to that shared set. An
     * application of a generated action operator contributes whatever its body settles.
     */
    private List<String> collectAssignedVars(
            TlaEx expression, Set<String> declared, Map<String, TlaEx> actionOperators) {
        if (!(expression instanceof OperEx operator)) {
            return List.of();
        }
        var arguments = CollectionConverters.asJava(operator.args());
        return switch (operator.oper().name()) {
            case "EQ", "SET_IN" -> {
                var assigned = primedName(arguments.getFirst());
                yield assigned != null && declared.contains(assigned)
                        ? List.of(assigned)
                        : List.of();
            }
            case "UNCHANGED" ->
                names(arguments.getFirst()).stream().filter(declared::contains).toList();
            case "AND" -> {
                var settled = new ArrayList<String>();
                arguments.forEach(argument ->
                        settled.addAll(collectAssignedVars(argument, declared, actionOperators)));
                yield settled;
            }
            case "OR" -> branchAssignedVars(arguments, declared, actionOperators);
            case "IF_THEN_ELSE" ->
                branchAssignedVars(
                        List.of(arguments.get(1), arguments.get(2)), declared, actionOperators);
            case "EXISTS3" -> collectAssignedVars(arguments.get(2), declared, actionOperators);
            case "OPER_APP" -> {
                var applied = arguments.getFirst();
                yield applied instanceof NameEx name && actionOperators.containsKey(name.name())
                        ? collectAssignedVars(actionOperators.get(name.name()), declared, actionOperators)
                        : List.of();
            }
            default -> List.of();
        };
    }

    /**
     * Returns the variable set every branch of a disjunction or IF-THEN-ELSE settles, asserting
     * that the branches agree on it and that none settles a variable twice.
     */
    private List<String> branchAssignedVars(
            List<TlaEx> branches, Set<String> declared, Map<String, TlaEx> actionOperators) {
        List<String> shared = null;
        for (var branch : branches) {
            var settled = collectAssignedVars(branch, declared, actionOperators);
            assertEquals(
                    settled.size(),
                    new LinkedHashSet<>(settled).size(),
                    "a variable is settled twice in a branch: " + print(branch));
            if (shared == null) {
                shared = settled;
            } else {
                assertEquals(
                        new LinkedHashSet<>(shared),
                        new LinkedHashSet<>(settled),
                        "action branches disagree on the variables they settle: " + print(branch));
            }
        }
        return shared == null ? List.of() : shared;
    }

    /** Reports whether {@code expression} applies any operator named in {@code names}. */
    private boolean appliesAny(TlaEx expression, Set<String> names) {
        if (!(expression instanceof OperEx operator)) {
            return false;
        }
        if (operator.oper().name().equals("OPER_APP")
                && operator.args().head() instanceof NameEx name
                && names.contains(name.name())) {
            return true;
        }
        return CollectionConverters.asJava(operator.args()).stream()
                .anyMatch(argument -> appliesAny(argument, names));
    }

    /** Reports whether {@code expression} contains an application of the named operator. */
    private boolean containsOperator(TlaEx expression, String name) {
        return expression instanceof OperEx operator
                && (operator.oper().name().equals(name)
                        || CollectionConverters.asJava(operator.args()).stream()
                                .anyMatch(argument -> containsOperator(argument, name)));
    }

    /** Returns the name a PRIME wraps, or {@code null} when the expression is not primed. */
    private String primedName(TlaEx expression) {
        if (expression instanceof OperEx operator
                && operator.oper().name().equals("PRIME")
                && operator.args().head() instanceof NameEx name) {
            return name.name();
        }
        return null;
    }

    /** Returns the names of a variable or of a tuple of variables. */
    private List<String> names(TlaEx expression) {
        if (expression instanceof NameEx name) {
            return List.of(name.name());
        }
        if (expression instanceof OperEx operator && operator.oper().name().equals("TUPLE")) {
            return CollectionConverters.asJava(operator.args()).stream()
                    .flatMap(argument -> names(argument).stream())
                    .toList();
        }
        return List.of();
    }

    private boolean containsPrime(TlaEx expression) {
        if (expression instanceof OperEx operator) {
            return operator.oper().name().equals("PRIME")
                    || CollectionConverters.asJava(operator.args()).stream()
                            .anyMatch(this::containsPrime);
        }
        if (expression instanceof LetInEx letIn) {
            return containsPrime(letIn.body())
                    || CollectionConverters.asJava(letIn.decls()).stream()
                            .anyMatch(declaration -> containsPrime(declaration.body()));
        }
        return false;
    }

    /**
     * Collects references to state variables. In Init the variables appear on the left of their
     * own conjuncts, which is not a read, so {@code skipConjunctHeads} passes over them.
     */
    private void collectFreeReads(
            TlaEx expression,
            Set<String> variables,
            List<String> reads,
            boolean skipConjunctHeads) {
        if (expression instanceof NameEx name) {
            if (variables.contains(name.name())) {
                reads.add(name.name());
            }
            return;
        }
        if (expression instanceof LetInEx letIn) {
            collectFreeReads(letIn.body(), variables, reads, false);
            CollectionConverters.asJava(letIn.decls())
                    .forEach(declaration ->
                            collectFreeReads(declaration.body(), variables, reads, false));
            return;
        }
        if (!(expression instanceof OperEx operator)) {
            return;
        }
        var arguments = CollectionConverters.asJava(operator.args());
        if (skipConjunctHeads && operator.oper().name().equals("AND")) {
            arguments.forEach(
                    argument -> collectFreeReads(argument, variables, reads, true));
            return;
        }
        if (skipConjunctHeads
                && (operator.oper().name().equals("EQ")
                        || operator.oper().name().equals("SET_IN"))) {
            collectFreeReads(arguments.get(1), variables, reads, false);
            return;
        }
        arguments.forEach(argument -> collectFreeReads(argument, variables, reads, false));
    }

    private List<String> variableNames(GeneratedSpec spec) {
        return spec.variables().stream().map(TlaVarDecl::name).toList();
    }

    private GeneratedSpec generate(byte[] input) {
        return IrGenerators.specs(IrGenerationConfig.defaults()).generate(input);
    }

    private void assertBuildsOrRejects(byte[] input) {
        assertBuildsOrRejects(IrGenerationConfig.defaults(), input);
    }

    private void assertBuildsOrRejects(IrGenerationConfig config, byte[] input) {
        try {
            assertFalse(render(IrGenerators.specs(config).generate(input)).isEmpty());
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
        spec.auxiliaryOperators()
                .forEach(operator ->
                        text.append(operator.name())
                                .append(" == ")
                                .append(print(operator.body()))
                                .append('\n'));
        spec.generated().forEach(expression -> text.append(print(expression)).append('\n'));
        return text.toString();
    }

    private String print(TlaEx expression) {
        var buffer = new StringWriter();
        var printWriter = new PrintWriter(buffer);
        new PrettyWriter(printWriter, new TextLayout(120, 2), new TlaDeclAnnotator())
                .write(expression);
        printWriter.flush();
        return buffer.toString().strip();
    }
}
