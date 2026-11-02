package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.TlaDecl;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.TemporalProperty;
import io.github.tlaplus.hardening.gen.ir.IrNames;
import io.github.tlaplus.hardening.gen.rewrite.Orientation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.UnaryOperator;
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
@ArchRole(Role.FACADE)
@Key
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

    /**
     * The action invariant of a metamorphic module (ADR 0016 §3): {@code [ActionC]_vars}, which
     * every transition of the explored side must satisfy. Apalache checks it as an action invariant.
     */
    public static final String STEP = "Step";

    /**
     * {@code [][Step]_vars}, the form in which TLC checks {@link #STEP}: TLC has no action invariant,
     * but checks an action property on every transition, without a tableau.
     */
    public static final String STEP_PROPERTY = "StepProperty";

    /**
     * Every definition some tool evaluates directly in every module; everything else is reached
     * through them. A metamorphic module also defines {@link #STEP} and {@link #STEP_PROPERTY}.
     */
    public static final List<String> ENTRY_POINTS = List.of(INIT, NEXT, INV, SPEC, PROP, LIVENESS);

    /** The entry points of a metamorphic module, which also checks an action invariant. */
    public static final List<String> RELATION_ENTRY_POINTS = List.of(
            INIT, NEXT, INV, SPEC, PROP, LIVENESS, STEP, STEP_PROPERTY);

    /** The checked side's fairness conditions in a metamorphic module (ADR 0027 §3). */
    public static final String FAIRNESS_C = "FairnessC";

    /** The checked side's next-state action, {@code ActionC \/ UNCHANGED vars}. */
    public static final String NEXT_C = "NextC";

    /**
     * {@code InitC /\ [][NextC]_vars /\ FairnessC}, the checked side's specification, under which
     * TLC checks {@link #PROP_C} (ADR 0027 §4).
     */
    public static final String SPEC_C = "SpecC";

    /** The checked side's property. */
    public static final String PROP_C = "PropC";

    /**
     * The entry points of a metamorphic module whose properties TLC checks in runs of their own
     * (ADR 0027 §4): {@link #PROP} under {@link #SPEC}, and {@link #PROP_C} under {@link #SPEC_C}.
     */
    public static final List<String> TEMPORAL_RELATION_ENTRY_POINTS = List.of(
            INIT, NEXT, INV, SPEC, PROP, LIVENESS, STEP, STEP_PROPERTY, SPEC_C, PROP_C);

    /**
     * Names of the side definitions of a metamorphic module (ADR 0016 §3), by role: E is the
     * explored side and C the checked one.
     */
    static final String INIT_E = "InitE";
    static final String INIT_C = "InitC";
    static final String INV_E = "InvE";
    static final String INV_C = "InvC";
    static final String ACTION_E = "ActionE";
    static final String ACTION_C = "ActionC";

    /**
     * Suffix of M2's generated operators. The generator never spells a name with an
     * underscore, so the renamed copies never collide with M1's.
     */
    static final String REWRITTEN_SUFFIX = "_2";

    private static final String VARIABLE_NAME = "exprValue";

    private FuzzInputModule() {}

    /**
     * Wraps one generated expression in the single-state module: the expression is both the
     * initial value of {@code exprValue} and the invariant asserted about it.
     */
    public static TlaModule create(TlaEx expression) {
        return create(expression, expression);
    }

    /**
     * Wraps two expressions of one type in the single-state module: {@code initial} is the initial
     * value of {@code exprValue}, and the invariant asserts that it equals {@code asserted}. With a
     * metamorphic pair, {@code exprE} is initial and {@code exprC} asserted (ADR 0016 §3).
     */
    public static TlaModule create(TlaEx initial, TlaEx asserted) {
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(asserted, "asserted");

        var builder = new TlaTypedScopeUncheckedBuilder();
        var expressionType = TlaTypes.typeOf(initial);
        var exprValue = TlaDeclarations.variable(VARIABLE_NAME, expressionType);
        // Apalache requires unique node identities, and the two sides may share subexpressions.
        var assertedCopy = TlaExpressions.deepCopy(asserted);

        var init = builder.eql(builder.varDeclAsNameEx(exprValue), initial);
        var next = builder.unchanged(builder.varDeclAsNameEx(exprValue));
        var invariant = builder.eql(builder.varDeclAsNameEx(exprValue), assertedCopy);
        return assemble(List.of(exprValue), List.of(exprValue),
                new Skeleton(init, next, invariant, Optional.empty(), Optional.empty()));
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
        return assemble(declarations, spec.variables(),
                new Skeleton(spec.initPredicate(), next, spec.invariant(), spec.property(), Optional.empty()));
    }

    /**
     * Assembles the implication relation of a metamorphic pair of modules M1 and M2 (ADR 0016 §3).
     * The pair shares one copy of the variables; M2's operators are renamed apart. The orientation
     * selects the explored side E and the checked side C:
     *
     * <pre>
     * Init == InitE
     * Next == ActionE \/ UNCHANGED vars
     * Inv  == (step = 0 => InitC) /\ (InvE <=> InvC)
     * Step == [ActionC]_vars
     * </pre>
     *
     * <p>Each side's body is a definition of its own, because a copied body repeats its labels.
     * {@code relation} decides whether the property and fairness are left out or, for a base with a
     * property, both sides' are defined for TLC to check apart (ADR 0027 §3):
     *
     * <pre>
     * Fairness  == FairnessE
     * Prop      == PropE
     * FairnessC == ...
     * NextC     == ActionC \/ UNCHANGED vars
     * SpecC     == InitC /\ [][NextC]_vars /\ FairnessC
     * PropC     == ...
     * </pre>
     */
    public static TlaModule create(
            GeneratedSpec original, GeneratedSpec rewritten, Orientation orientation, PropertyRelation relation) {
        Objects.requireNonNull(original, "original");
        Objects.requireNonNull(rewritten, "rewritten");
        Objects.requireNonNull(orientation, "orientation");
        Objects.requireNonNull(relation, "relation");
        var builder = new TlaTypedScopeUncheckedBuilder();
        var renamed = new HashMap<String, String>();
        rewritten.operators().forEach(operator -> renamed.put(
                operator.declaration().name(), operator.declaration().name() + REWRITTEN_SUFFIX));
        UnaryOperator<String> rename = name -> renamed.getOrDefault(name, name);

        var declarations = new ArrayList<TlaDecl>(original.variables());
        original.operators().forEach(operator -> declarations.add(operator.declaration()));
        // The sides may share subexpressions, and Apalache requires unique node identities.
        rewritten.operators().forEach(operator -> declarations.add(
                TlaDeclarations.deepCopy(IrNames.rename(operator.declaration(), rename))));
        UnaryOperator<TlaEx> second = body -> TlaExpressions.deepCopy(IrNames.rename(body, rename));
        var init2 = second.apply(rewritten.initPredicate());
        var invariant2 = second.apply(rewritten.invariant());
        var action2 = second.apply(rewritten.nextAction());
        declarations.add(builder.decl(INIT_E, orientation.explored(original.initPredicate(), init2)));
        declarations.add(builder.decl(INIT_C, orientation.checked(original.initPredicate(), init2)));
        declarations.add(builder.decl(INV_E, orientation.explored(original.invariant(), invariant2)));
        declarations.add(builder.decl(INV_C, orientation.checked(original.invariant(), invariant2)));
        declarations.add(builder.decl(ACTION_E, orientation.explored(original.nextAction(), action2)));
        declarations.add(builder.decl(ACTION_C, orientation.checked(original.nextAction(), action2)));

        var variables = original.variables();
        var step = original.variables().stream()
                .filter(variable -> variable.name().equals(GeneratedSpec.STEP_VARIABLE))
                .findFirst().orElseThrow();
        var init = reference(builder, INIT_E);
        var next = builder.or(reference(builder, ACTION_E), builder.unchanged(variablesTuple(builder, variables)));
        var invariant = builder.and(
                builder.implies(builder.eql(builder.varDeclAsNameEx(step), builder.integer(0)),
                        reference(builder, INIT_C)),
                builder.equiv(reference(builder, INV_E), reference(builder, INV_C)));
        var stepAction = builder.stutter(reference(builder, ACTION_C), variablesTuple(builder, variables));
        if (relation.forBase(original) == PropertyRelation.OMITTED) {
            return assemble(declarations, variables, new Skeleton(init, next, invariant, Optional.empty(),
                    Optional.of(new RelationParts(stepAction, List.of()))));
        }
        var property1 = original.property().orElseThrow();
        var property2 = rewritten.property().orElseThrow();
        var fairness2 = property2.fairness().stream().map(second).toList();
        var formula2 = second.apply(property2.formula());
        var checkedSide = List.<TlaDecl>of(
                builder.decl(FAIRNESS_C, conjunction(builder, orientation.checked(property1.fairness(), fairness2))),
                builder.decl(NEXT_C, builder.or(reference(builder, ACTION_C),
                        builder.unchanged(variablesTuple(builder, variables)))),
                builder.decl(SPEC_C, builder.and(
                        reference(builder, INIT_C),
                        builder.always(builder.stutter(reference(builder, NEXT_C), variablesTuple(builder, variables))),
                        reference(builder, FAIRNESS_C))),
                builder.decl(PROP_C, orientation.checked(property1.formula(), formula2)));
        var explored = new TemporalProperty(orientation.explored(property1.fairness(), fairness2),
                orientation.explored(property1.formula(), formula2));
        return assemble(declarations, variables, new Skeleton(init, next, invariant, Optional.of(explored),
                Optional.of(new RelationParts(stepAction, checkedSide))));
    }

    /**
     * The generated bodies of the fixed entry points. Without a property, the fairness and the
     * property are {@code TRUE}; a metamorphic module also has its relation's parts.
     */
    private record Skeleton(TlaEx init, TlaEx next, TlaEx invariant, Optional<TemporalProperty> property,
            Optional<RelationParts> relation) {}

    /**
     * What only a metamorphic module defines: its action invariant, and the checked side's
     * specification and property when TLC checks them apart (ADR 0027 §3).
     */
    private record RelationParts(TlaEx stepAction, List<TlaDecl> checkedSide) {}

    /** Appends the fixed entry points once, in the order emitted to every tool. */
    private static TlaModule assemble(
            List<? extends TlaDecl> prefix, List<TlaVarDecl> variables, Skeleton skeleton) {
        var builder = new TlaTypedScopeUncheckedBuilder();
        var declarations = new ArrayList<TlaDecl>(prefix);
        declarations.add(builder.decl(INIT, skeleton.init()));
        declarations.add(builder.decl(NEXT, skeleton.next()));
        declarations.add(builder.decl(INV, skeleton.invariant()));
        var fairness = skeleton.property().map(TemporalProperty::fairness).orElse(List.of());
        var property = skeleton.property().map(temporal -> List.of(temporal.formula())).orElse(List.<TlaEx>of());
        declarations.add(builder.decl(FAIRNESS, conjunction(builder, fairness)));
        declarations.add(builder.decl(SPEC, builder.and(
                reference(builder, INIT),
                builder.always(builder.stutter(reference(builder, NEXT), variablesTuple(builder, variables))),
                reference(builder, FAIRNESS))));
        declarations.add(builder.decl(PROP, conjunction(builder, property)));
        declarations.add(builder.decl(LIVENESS,
                builder.implies(reference(builder, FAIRNESS), reference(builder, PROP))));
        skeleton.relation().ifPresent(relation -> {
            declarations.add(builder.decl(STEP, relation.stepAction()));
            declarations.add(builder.decl(STEP_PROPERTY,
                    builder.always(builder.stutter(reference(builder, STEP), variablesTuple(builder, variables)))));
            declarations.addAll(relation.checkedSide());
        });
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
