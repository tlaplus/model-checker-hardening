package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaDecl;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.TemporalProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;

/**
 * Assembles the module checked by the parser and model-checker stages.
 *
 * <p>Every generated artifact reaches the tools through this one assembler, whatever the generator
 * produced. The entry-point names below are the contract between the assembled module and the
 * fixed tool invocations: TLC's configuration file and Apalache's command line name them, so they
 * live here rather than being spelled again at each call site.
 *
 * <p>Every module defines every entry point. A module without a temporal property defines the
 * fairness and the property as {@code TRUE}, and its checkers are not asked about them.
 */
public final class FuzzInputModule {
    /** Name of the assembled module, and therefore of the file written for each tool. */
    public static final String MODULE_NAME = "FuzzInput";

    /** Initial-state predicate. */
    public static final String INIT = "Init";

    /**
     * Next-state action. A generated module's action ends with a stuttering disjunct over every
     * variable, because Apalache, unlike TLC, adds no stuttering step of its own.
     */
    public static final String NEXT = "Next";

    /** State invariant. */
    public static final String INV = "Inv";

    /** Conjunction of the weak and strong fairness conditions. */
    public static final String FAIRNESS = "Fairness";

    /** {@code Init /\ [][Next]_vars /\ Fairness}, which TLC checks as its specification. */
    public static final String SPEC = "Spec";

    /** The temporal property TLC checks against {@link #SPEC}. */
    public static final String PROP = "Prop";

    /**
     * {@code Fairness => Prop}, the temporal property Apalache checks. Apalache supports no
     * fairness in a specification, so the implication asks it what TLC answers under {@link #SPEC}.
     */
    public static final String LIVENESS = "Liveness";

    /** Every definition some tool evaluates directly; everything else is reached through them. */
    public static final List<String> ENTRY_POINTS = List.of(INIT, NEXT, INV, SPEC, PROP, LIVENESS);

    private static final String VARIABLE_NAME = "exprValue";

    private FuzzInputModule() {}

    /**
     * Wraps one generated expression in the single-state module: the expression is both the
     * initial value of {@code exprValue} and the invariant asserted about it.
     */
    public static TlaModule create(TlaEx expression) {
        Objects.requireNonNull(expression, "expression");

        var builder = new TlaTypedScopeUncheckedBuilder();
        var expressionType = TlaTypes.typeOf(expression);
        var exprValue = TlaDeclarations.variable(VARIABLE_NAME, expressionType);
        // Apalache requires unique node identities, and the expression appears twice.
        var expressionCopy = TlaExpressions.deepCopy(expression);

        var init = builder.eql(builder.varDeclAsNameEx(exprValue), expression);
        var next = builder.unchanged(builder.varDeclAsNameEx(exprValue));
        var invariant = builder.eql(builder.varDeclAsNameEx(exprValue), expressionCopy);
        return assemble(List.of(exprValue), List.of(exprValue),
                new Skeleton(init, next, invariant, List.of(), List.of()));
    }

    /**
     * Assembles a generated module and closes its next-state action with a stuttering disjunct.
     * <p>The generated declarations keep their names; only the entry points are fixed, so the tool
     * invocations need not change with the input.
     */
    public static TlaModule create(GeneratedSpec spec) {
        Objects.requireNonNull(spec, "spec");
        var builder = new TlaTypedScopeUncheckedBuilder();
        var declarations = new ArrayList<TlaDecl>(spec.variables());
        spec.operators().forEach(operator -> declarations.add(operator.declaration()));
        var next = builder.or(spec.nextAction(), builder.unchanged(variablesTuple(builder, spec.variables())));
        var fairness = spec.property().map(TemporalProperty::fairness).orElse(List.of());
        var property = spec.property().map(TemporalProperty::conjuncts).orElse(List.of());
        return assemble(declarations, spec.variables(),
                new Skeleton(spec.initPredicate(), next, spec.invariant(), fairness, property));
    }

    /** The generated bodies of the fixed entry points. An empty list conjoins to {@code TRUE}. */
    private record Skeleton(TlaEx init, TlaEx next, TlaEx invariant, List<TlaEx> fairness, List<TlaEx> property) {}

    /** Appends the fixed entry points once, in the order emitted to every tool. */
    private static TlaModule assemble(
            List<? extends TlaDecl> prefix, List<TlaVarDecl> variables, Skeleton skeleton) {
        var builder = new TlaTypedScopeUncheckedBuilder();
        var declarations = new ArrayList<TlaDecl>(prefix);
        declarations.add(builder.decl(INIT, skeleton.init()));
        declarations.add(builder.decl(NEXT, skeleton.next()));
        declarations.add(builder.decl(INV, skeleton.invariant()));
        declarations.add(builder.decl(FAIRNESS, conjunction(builder, skeleton.fairness())));
        declarations.add(builder.decl(SPEC, builder.and(
                reference(builder, INIT),
                builder.always(builder.stutter(reference(builder, NEXT), variablesTuple(builder, variables))),
                reference(builder, FAIRNESS))));
        declarations.add(builder.decl(PROP, conjunction(builder, skeleton.property())));
        declarations.add(builder.decl(LIVENESS,
                builder.implies(reference(builder, FAIRNESS), reference(builder, PROP))));
        return TlaModules.create(MODULE_NAME, declarations);
    }

    /** Returns {@code TRUE}, the only conjunct, or the conjunction of the supplied conjuncts. */
    private static TlaEx conjunction(TlaTypedScopeUncheckedBuilder builder, List<TlaEx> conjuncts) {
        return switch (conjuncts.size()) {
            case 0 -> builder.bool(true);
            case 1 -> conjuncts.getFirst();
            default -> builder.and(conjuncts.toArray(TlaEx[]::new));
        };
    }

    /** Returns an application of one of the nullary Boolean entry points defined above it. */
    private static TlaEx reference(TlaTypedScopeUncheckedBuilder builder, String name) {
        return builder.operApply(builder.name(name, TlaTypes.operator(TlaTypes.BOOL)));
    }

    /** Returns the tuple of every declared variable, in declaration order. */
    private static TlaEx variablesTuple(TlaTypedScopeUncheckedBuilder builder, List<TlaVarDecl> variables) {
        return builder.tuple(variables.stream().map(builder::varDeclAsNameEx).toArray(TlaEx[]::new));
    }
}
