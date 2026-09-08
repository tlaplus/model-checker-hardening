package io.github.tlaplus.hardening.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import static io.github.tlaplus.hardening.common.ScalaCollections.list;
import static io.github.tlaplus.hardening.common.ScalaCollections.seq;

/** IR traversal and recursive action-accounting assertions shared by generator tests. */
public final class TlaIrTestSupport {
    private TlaIrTestSupport() {}

    /** Returns the top-level disjuncts of the next-state action. */
    public static List<TlaEx> disjuncts(TlaEx nextAction) {
        if (nextAction instanceof OperEx operator && operator.oper().name().equals("OR")) {
            return list(operator.args()).stream().toList();
        }
        return List.of(nextAction);
    }

    public static List<GeneratedActionOperator> actionOperators(GeneratedSpec spec) {
        return spec.operators().stream().filter(GeneratedActionOperator.class::isInstance)
                .map(GeneratedActionOperator.class::cast).toList();
    }

    public static List<TlaOperDecl> auxiliaryOperators(GeneratedSpec spec) {
        return spec.operators().stream().filter(GeneratedOperator.Auxiliary.class::isInstance)
                .map(GeneratedOperator::declaration).toList();
    }

    /** Maps each generated action operator's name to its body. */
    public static Map<String, TlaEx> actionOperatorBodies(GeneratedSpec spec) {
        return actionOperators(spec).stream()
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
    public static List<String> collectAssignedVars(
            TlaEx expression, Set<String> declared, Map<String, TlaEx> actionOperators) {
        if (!(expression instanceof OperEx operator)) {
            return List.of();
        }
        var arguments = list(operator.args());
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
    private static List<String> branchAssignedVars(
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
    public static boolean appliesAny(TlaEx expression, Set<String> names) {
        return walk(expression).anyMatch(node -> node instanceof OperEx operator
                && operator.oper().name().equals("OPER_APP")
                && operator.args().head() instanceof NameEx name && names.contains(name.name()));
    }

    /** Reports whether {@code expression} contains an application of the named operator. */
    public static boolean containsOperator(TlaEx expression, String name) {
        return walk(expression).anyMatch(node -> node instanceof OperEx operator
                && operator.oper().name().equals(name));
    }

    private static java.util.stream.Stream<TlaEx> walk(TlaEx expression) {
        var children = switch (expression) {
            case OperEx operator -> list(operator.args()).stream();
            case LetInEx let -> java.util.stream.Stream.concat(java.util.stream.Stream.of(let.body()),
                    list(let.decls()).stream().map(TlaOperDecl::body));
            default -> java.util.stream.Stream.<TlaEx>empty();
        };
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(expression),
                children.flatMap(TlaIrTestSupport::walk));
    }

    /** Whether every path assigns a requested variable, rather than merely leaving it UNCHANGED. */
    public static boolean guaranteesAssignment(TlaEx expression, Set<String> variables,
                                               Map<String, TlaEx> operators) {
        if (!(expression instanceof OperEx operator)) return false;
        var args = list(operator.args());
        return switch (operator.oper().name()) {
            case "EQ", "SET_IN" -> {
                var name = primedName(args.getFirst());
                yield name != null && variables.contains(name);
            }
            case "AND" -> args.stream().anyMatch(arg -> guaranteesAssignment(arg, variables, operators));
            case "OR" -> args.stream().allMatch(arg -> guaranteesAssignment(arg, variables, operators));
            case "IF_THEN_ELSE" -> args.subList(1, 3).stream()
                    .allMatch(arg -> guaranteesAssignment(arg, variables, operators));
            case "EXISTS3" -> guaranteesAssignment(args.get(2), variables, operators);
            case "OPER_APP" -> args.getFirst() instanceof NameEx name && operators.containsKey(name.name())
                    && guaranteesAssignment(operators.get(name.name()), variables, operators);
            default -> false;
        };
    }

    /** Returns the name a PRIME wraps, or {@code null} when the expression is not primed. */
    private static String primedName(TlaEx expression) {
        if (expression instanceof OperEx operator
                && operator.oper().name().equals("PRIME")
                && operator.args().head() instanceof NameEx name) {
            return name.name();
        }
        return null;
    }

    /** Returns the names of a variable or of a tuple of variables. */
    private static List<String> names(TlaEx expression) {
        if (expression instanceof NameEx name) {
            return List.of(name.name());
        }
        if (expression instanceof OperEx operator && operator.oper().name().equals("TUPLE")) {
            return list(operator.args()).stream()
                    .flatMap(argument -> names(argument).stream())
                    .toList();
        }
        return List.of();
    }

    public static boolean containsPrime(TlaEx expression) {
        return containsOperator(expression, "PRIME");
    }

    /**
     * Collects references to state variables. In Init the variables appear on the left of their
     * own conjuncts, which is not a read, so {@code skipConjunctHeads} passes over them.
     */
    public static void collectFreeReads(
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
            list(letIn.decls())
                    .forEach(declaration ->
                            collectFreeReads(declaration.body(), variables, reads, false));
            return;
        }
        if (!(expression instanceof OperEx operator)) {
            return;
        }
        var arguments = list(operator.args());
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

    public static List<String> variableNames(GeneratedSpec spec) {
        return spec.variables().stream().map(TlaVarDecl::name).toList();
    }

    public static String print(TlaEx expression) {
        var buffer = new StringWriter();
        var printWriter = new PrintWriter(buffer);
        new PrettyWriter(printWriter, new TextLayout(120, 2), new TlaDeclAnnotator())
                .write(expression);
        printWriter.flush();
        return buffer.toString().strip();
    }
}
