package io.github.tlaplus.hardening.workflow.spec;

import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaDecl;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.TlaVarDecl;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.InputRejectedException;
import io.github.tlaplus.hardening.gen.TemporalProperty;
import io.github.tlaplus.hardening.gen.ir.IrNames;
import io.github.tlaplus.hardening.gen.rewrite.Orientation;
import io.github.tlaplus.hardening.gen.trace.Trace;
import io.github.tlaplus.hardening.gen.trace.TraceEnding;
import io.github.tlaplus.hardening.gen.trace.TraceLiterals;
import io.github.tlaplus.hardening.workflow.worker.CheckKind;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaModules;
import org.apalache_mc.tla.jir.TlaOperators;
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
     * The property and fairness definitions of both sides, whose temporal shapes the quality gate
     * rewards (ADR 0028). A module without property runs defines only the explored side's.
     */
    public static final List<String> PROPERTY_DEFINITIONS = List.of(PROP, FAIRNESS, PROP_C, FAIRNESS_C);

    /**
     * The definitions that a checker invocation of one side reads: TLC explores {@code
     * specification}, and Apalache {@code init} and {@code next} (ADR 0029 §1). Both check {@code
     * property}.
     */
    public record SideEntryPoints(String init, String next, String specification, String property) {}

    /** Returns the definitions that a checker invocation of {@code side} reads. */
    public static SideEntryPoints entryPoints(CheckKind.Side side) {
        return switch (Objects.requireNonNull(side, "side")) {
            case EXPLORED -> new SideEntryPoints(INIT, NEXT, SPEC, PROP);
            case CHECKED -> new SideEntryPoints(INIT_C, NEXT_C, SPEC_C, PROP_C);
        };
    }

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

    /** The base's initial predicate in a replay module (ADR 0034 §3). */
    public static final String BASE_INIT = "BaseInit";

    /** The base's next-state action, stuttering disjunct included, in a replay module. */
    public static final String BASE_NEXT = "BaseNext";

    /** The base's invariant in a replay module. */
    public static final String BASE_INV = "BaseInv";

    /** The base's temporal definitions, which a replay module defines again as {@code TRUE}. */
    private static final Set<String> REPLAY_DROPPED = Set.of(FAIRNESS, SPEC, PROP, LIVENESS);

    /** The variable that counts the replayed transitions; no decoder draws this name. */
    public static final String REPLAY_INDEX = "idx";

    /**
     * Assembles the module that replays {@code trace} against an assembled base module (ADR 0034
     * §3). The base's {@code Init}, {@code Next} and {@code Inv} become {@link #BASE_INIT}, {@link
     * #BASE_NEXT} and {@link #BASE_INV}; its fairness, specification and properties are dropped.
     * With the trace's states s0, ..., sn as literals:
     *
     * <pre>
     * Init == v1 = s0.v1 /\ ... /\ idx = 0 /\ BaseInit
     * Next == \/ idx = 0 /\ v1' = s1.v1 /\ ... /\ idx' = 1 /\ BaseNext
     *         ...
     * Inv  == idx /= n \/ Last
     * </pre>
     *
     * <p>{@code Last} is {@code BaseInv} when sn claims to violate the invariant, {@code <<v1, ...>>
     * /= <<sl.v1, ...>>} for a lasso back to sl, and absent otherwise. The trace's equalities come
     * first, so TLC binds every variable from a constant and the base only checks it, and Apalache's
     * assignment finder takes them. The base's {@code UNCHANGED e} becomes {@code e' = e}: Apalache
     * desugars {@code UNCHANGED} into assignments, which it would reject as second assignments.
     * Without transitions, {@code Next} only stutters.
     *
     * @throws io.github.tlaplus.hardening.gen.InputRejectedException if a recorded value does not
     *     have its variable's type, or the trace's width is not the base's
     */
    public static TlaModule replay(TlaModule base, Trace trace) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(trace, "trace");
        var builder = new TlaTypedScopeUncheckedBuilder();
        var renamed = Map.of(INIT, BASE_INIT, NEXT, BASE_NEXT, INV, BASE_INV);
        var declarations = new ArrayList<TlaDecl>();
        var baseVariables = new ArrayList<TlaVarDecl>();
        for (var declaration : TlaModules.declarations(base)) {
            if (declaration instanceof TlaVarDecl variable) {
                baseVariables.add(variable);
                declarations.add(variable);
            } else if (declaration instanceof TlaOperDecl operator && !REPLAY_DROPPED.contains(operator.name())) {
                var checking = TlaDeclarations.rewrite(operator, FuzzInputModule::unchangedAsEquality);
                declarations.add(renamed.containsKey(operator.name())
                        ? TlaDeclarations.withHeader(checking, renamed.get(operator.name()), List.of())
                        : checking);
            } else if (!REPLAY_DROPPED.contains(declaration.name())) {
                declarations.add(declaration);
            }
        }
        if (trace.width() != baseVariables.size()) {
            throw new InputRejectedException("a trace of " + trace.width() + " variables replays a module of "
                    + baseVariables.size());
        }
        var index = TlaDeclarations.variable(REPLAY_INDEX, TlaTypes.INT);
        declarations.add(baseVariables.size(), index);
        var variables = new ArrayList<>(baseVariables);
        variables.add(index);
        var literals = new TraceLiterals();
        var states = trace.states();
        var init = new ArrayList<TlaEx>();
        for (var position = 0; position < baseVariables.size(); position++) {
            init.add(builder.eql(builder.varDeclAsNameEx(baseVariables.get(position)),
                    literals.literal(states.getFirst().get(position), TlaTypes.typeOf(baseVariables.get(position)))));
        }
        init.add(builder.eql(builder.varDeclAsNameEx(index), builder.integer(0)));
        init.add(reference(builder, BASE_INIT));
        var steps = new ArrayList<TlaEx>();
        for (var step = 1; step <= trace.transitions(); step++) {
            var conjuncts = new ArrayList<TlaEx>();
            conjuncts.add(builder.eql(builder.varDeclAsNameEx(index), builder.integer(step - 1)));
            for (var position = 0; position < baseVariables.size(); position++) {
                conjuncts.add(builder.eql(builder.prime(builder.varDeclAsNameEx(baseVariables.get(position))),
                        literals.literal(states.get(step).get(position), TlaTypes.typeOf(baseVariables.get(position)))));
            }
            conjuncts.add(builder.primeEq(builder.varDeclAsNameEx(index), builder.integer(step)));
            conjuncts.add(reference(builder, BASE_NEXT));
            steps.add(builder.and(conjuncts.toArray(TlaEx[]::new)));
        }
        var next = switch (steps.size()) {
            case 0 -> builder.unchanged(variablesTuple(builder, variables));
            case 1 -> steps.getFirst();
            default -> builder.or(steps.toArray(TlaEx[]::new));
        };
        var reached = builder.neql(builder.varDeclAsNameEx(index), builder.integer(trace.transitions()));
        var invariant = switch (trace.ending()) {
            case TraceEnding.Invariant() -> builder.or(reached, reference(builder, BASE_INV));
            case TraceEnding.Loop(var loop) -> {
                var loopState = new ArrayList<TlaEx>();
                for (var position = 0; position < baseVariables.size(); position++) {
                    loopState.add(literals.literal(states.get(loop).get(position),
                            TlaTypes.typeOf(baseVariables.get(position))));
                }
                yield builder.or(reached, builder.neql(variablesTuple(builder, baseVariables),
                        builder.tuple(loopState.toArray(TlaEx[]::new))));
            }
            case TraceEnding.Prefix() -> reached;
        };
        return assemble(declarations, variables, new Skeleton(builder.and(init.toArray(TlaEx[]::new)), next, invariant,
                Optional.empty(), Optional.empty()));
    }

    /**
     * Replaces {@code UNCHANGED e} by {@code e' = e}, which means the same (ADR 0034 §3). Apalache
     * desugars {@code UNCHANGED} into assignments {@code x' := x}, and rejects them once the trace's
     * equalities have assigned every variable; an equality it checks as a constraint.
     */
    private static TlaEx unchangedAsEquality(TlaEx expression) {
        return TlaExpressions.rewrite(expression, node -> {
            if (node instanceof OperEx application && application.oper() == TlaOperators.UNCHANGED) {
                var builder = new TlaTypedScopeUncheckedBuilder();
                var unchanged = TlaExpressions.arguments(application).getFirst();
                return builder.eql(builder.prime(unchanged), TlaExpressions.deepCopy(unchanged));
            }
            return node;
        });
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
