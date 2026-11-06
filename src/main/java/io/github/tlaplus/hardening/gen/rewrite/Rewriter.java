package io.github.tlaplus.hardening.gen.rewrite;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import io.github.tlaplus.hardening.common.ArchRole;
import io.github.tlaplus.hardening.common.Key;
import io.github.tlaplus.hardening.common.Role;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.GeneratedActionOperator;
import io.github.tlaplus.hardening.gen.GeneratedOperator;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.TemporalProperty;
import io.github.tlaplus.hardening.gen.engine.OperandGenerator;
import io.github.tlaplus.hardening.gen.ir.IrAlpha;
import io.github.tlaplus.hardening.gen.ir.IrBinding;
import io.github.tlaplus.hardening.gen.ir.IrNames;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaOperators;
import org.apalache_mc.tla.jir.TlaTypes;

/**
 * Decodes a rewrite payload into a rewrite of a base (ADR 0016 §2).
 *
 * <ul>
 *   <li>The first Boolean marker selects the {@link Orientation}.
 *   <li>The walk visits each rewritable body in pre-order and tracks its lexical scope; the
 *       bodies of a module and their order are those of {@link #rewriteSpec}.
 *   <li>At a node where some rule applies, one Boolean marker decides whether to rewrite it; an odd
 *       marker is followed by a fixed-width two-byte index over the weighted slots of the rules that
 *       apply, in declaration order, as {@code IrExprGenFactory} selects forms. A node where no rule
 *       applies reads no byte.
 *   <li>The walk re-examines the replacement, so rewrites stack up to {@link
 *       RewriteLimits#maximumRewriteDepth()}, and then continues into it. A copy of the rewritten
 *       node inside its replacement, like the x of x + 0, is the same node and continues its stack.
 *       {@link RewriteLimits#maximumRewrites()} bounds the rewrites of one body.
 *   <li>A rule applies only while the body stays within {@link RewriteLimits#maximumGrowth()}
 *       times its original size, plus the node budget of one fresh operand. The size of a
 *       replacement is estimated without bytes: each parameter counts the size of its binding, and
 *       a fresh parameter one node, so the rewrite that crosses the bound overshoots it by its
 *       fresh operands at most, and no rule applies to the body after it.
 *   <li>A rule that adds operators of temporal level applies only while the body has at most
 *       {@link RewriteLimits#maximumTemporalOperators()} of them, estimated in the same way with
 *       none for a fresh parameter (ADR 0030). A rule that adds none applies to a body above the
 *       limit too.
 *   <li>Exhausted input reads even markers, so the rest of the body stays as it is.
 * </ul>
 *
 * <p>Matching is byte-free. A fresh parameter, and any type variable the match leaves open, is
 * drawn from the rewrite payload through {@link OperandGenerator}.
 *
 * <p>With temporal rules (ADR 0027 §2), the walk first rewrites each fairness condition and then the
 * property formula, with the rules followed by the temporal rules. A rule that raises the level
 * applies there only at a temporal position, reached from the body's root through operands that
 * pass a temporal formula on. Without temporal rules, the walk reads exactly the bytes it read
 * before they existed.
 */
@ArchRole(Role.FACADE)
@Key
public final class Rewriter {
    /** Bytes of one rule index, as for an expression form (ADR 0016 §2). */
    static final int SELECTION_BYTES = 2;

    private final RewriteLibrary library;
    /** The rules of the property and the fairness conditions; absent without temporal rules. */
    private final Optional<RewriteLibrary> propertyLibrary;
    private final IrGenerationConfig generation;
    private final RewriteLimits limits;

    /** Returns a rewriter that leaves properties and fairness as they are. */
    public Rewriter(RewriteLibrary library, IrGenerationConfig generation, RewriteLimits limits) {
        this(library, RewriteLibrary.empty(), generation, limits);
    }

    /**
     * Returns a rewriter that also rewrites properties and fairness with {@code library} followed by
     * {@code temporal}, unless {@code temporal} is empty (ADR 0027 §2).
     *
     * @throws IllegalArgumentException if both libraries define a rule of one name
     */
    public Rewriter(RewriteLibrary library, RewriteLibrary temporal, IrGenerationConfig generation, RewriteLimits limits) {
        this.library = Objects.requireNonNull(library, "library");
        Objects.requireNonNull(temporal, "temporal");
        this.propertyLibrary = temporal.isEmpty() ? Optional.empty() : Optional.of(library.followedBy(temporal));
        this.generation = Objects.requireNonNull(generation, "generation");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Whether this rewriter rewrites the property and fairness of a module (ADR 0027 §2). */
    public boolean rewritesProperties() {
        return propertyLibrary.isPresent();
    }

    /** Rewrites one expression, the single body of an {@code expr} input. */
    public Rewrite<TlaEx> rewriteExpression(TlaEx expression, Draw draw) {
        Objects.requireNonNull(expression, "expression");
        var orientation = Orientation.of(draw.drawBoolean());
        var walk = new Walk(draw, new OperandGenerator(generation, IrNames.used(expression)));
        var rewritten = walk.body(expression, List.of());
        return new Rewrite<>(expression, rewritten, orientation, walk.applied);
    }

    /** A node rewritten above, whose copies in its replacement continue its stack of rewrites. */
    private record Stack(TlaEx origin, int depth) {}

    /**
     * Rewrites a module: its operator definitions in declaration order, then {@code Init}, the
     * next-state action and the invariant, each under its own budget of rewrites. Every body sees
     * the scope the generator drew it in: an auxiliary operator its parameters and the operators
     * before it, an action operator also the variables, {@code Init} only the auxiliary operators,
     * the next-state action the variables but not {@code step}, and the invariant everything.
     *
     * <p>With temporal rules, each fairness condition and then the property formula come first, in
     * the scope of the invariant: a rewrite payload is short, and the bodies before it would read
     * all of its bytes (ADR 0027 §2). Without temporal rules, the property is left as it is.
     */
    public Rewrite<GeneratedSpec> rewriteSpec(GeneratedSpec spec, Draw draw) {
        Objects.requireNonNull(spec, "spec");
        var orientation = Orientation.of(draw.drawBoolean());
        var names = new LinkedHashSet<String>();
        spec.variables().forEach(variable -> names.add(variable.name()));
        spec.operators().forEach(operator -> names.add(operator.declaration().name()));
        spec.generated().forEach(body -> names.addAll(IrNames.used(body)));
        var walk = new Walk(draw, new OperandGenerator(generation, names));

        var scopes = ModuleScopes.of(spec);
        var property = spec.property();
        if (propertyLibrary.isPresent() && property.isPresent()) {
            var fairness = new ArrayList<TlaEx>();
            for (var condition : property.get().fairness()) {
                fairness.add(walk.formula(condition, scopes.state(), propertyLibrary.get()));
            }
            var formula = walk.formula(property.get().formula(), scopes.state(), propertyLibrary.get());
            property = Optional.of(new TemporalProperty(fairness, formula));
        }

        var operators = new ArrayList<GeneratedOperator>();
        var declared = 0;
        for (var operator : spec.operators()) {
            var declaration = operator.declaration();
            // An operator sees the auxiliary operators declared before it.
            var scope = new ArrayList<>(scopes.auxiliaries().subList(0, declared));
            if (operator instanceof GeneratedActionOperator) {
                scope.addAll(scopes.variables());
            }
            TlaDeclarations.parameters(declaration).forEach(parameter -> scope.add(
                    new OperandGenerator.Name(parameter.name(), parameter.type(), OperandGenerator.Kind.DEFINITION)));
            var body = walk.body(declaration.body(), scope);
            var rewritten = body == declaration.body() ? declaration : TlaDeclarations.withBody(declaration, body);
            operators.add(switch (operator) {
                case GeneratedOperator.Auxiliary ignored -> new GeneratedOperator.Auxiliary(rewritten);
                case GeneratedActionOperator action -> new GeneratedActionOperator(rewritten, action.effect());
            });
            if (operator instanceof GeneratedOperator.Auxiliary) {
                declared++;
            }
        }
        var initialized = new HashSet<String>();
        spec.variables().forEach(variable -> initialized.add(variable.name()));
        // Init assigns the unprimed variables, so they keep their assigning positions there.
        var init = walk.body(spec.initPredicate(), scopes.auxiliaries(), initialized);
        var next = walk.body(spec.nextAction(), scopes.action());
        var invariant = walk.body(spec.invariant(), scopes.state());
        var rewritten = new GeneratedSpec(spec.variables(), operators, init, next, invariant, property, spec.stepBound());
        return new Rewrite<>(spec, rewritten, orientation, walk.applied);
    }

    /** The state of one decoding: its cursor, its name and operand supply, and the rules applied. */
    private final class Walk {
        private final Draw draw;
        private final OperandGenerator operands;
        private final List<String> applied = new ArrayList<>();
        /** The rules of the current body. */
        private RewriteLibrary rules = library;
        private int rewrites;
        /** The nodes and temporal operators of the body as rewritten so far. */
        private Extent body;
        private int sizeLimit;
        private Set<String> assigned = Set.of();
        /** The {@code EXCEPT} replacements the walk is in, where a fresh operand may hold no label. */
        private int exceptReplacements;

        Walk(Draw draw, OperandGenerator operands) {
            this.draw = Objects.requireNonNull(draw, "draw");
            this.operands = operands;
        }

        /** Rewrites one body of an action or a state predicate under its own budget of rewrites. */
        TlaEx body(TlaEx expression, List<OperandGenerator.Name> scope) {
            return body(expression, scope, Set.of());
        }

        /**
         * Rewrites one body under its own budget of rewrites; {@code assigned} names the unprimed
         * variables TLC assigns in it, which are those of {@code Init}.
         */
        TlaEx body(TlaEx expression, List<OperandGenerator.Name> scope, Set<String> assigned) {
            return body(expression, scope, assigned, library, false);
        }

        /**
         * Rewrites a fairness condition or the property formula with {@code formulaRules}; its root
         * is a temporal position (ADR 0027 §2).
         */
        TlaEx formula(TlaEx expression, List<OperandGenerator.Name> scope, RewriteLibrary formulaRules) {
            return body(expression, scope, Set.of(), formulaRules, true);
        }

        private TlaEx body(TlaEx expression, List<OperandGenerator.Name> scope, Set<String> assigned,
                RewriteLibrary bodyRules, boolean temporal) {
            this.assigned = assigned;
            this.rules = bodyRules;
            rewrites = 0;
            body = Extent.of(expression);
            sizeLimit = body.nodes() * limits.maximumGrowth() + freshSize();
            return visit(expression, scope, null, temporal);
        }

        /** Visits {@code node}; {@code temporal} tells whether a temporal formula may stand there. */
        private TlaEx visit(TlaEx node, List<OperandGenerator.Name> scope, Stack stack, boolean temporal) {
            var continued = stack != null && IrAlpha.equivalent(node, stack.origin());
            var stacked = continued ? stack.depth() : 0;
            var current = node;
            for (; stacked < limits.maximumRewriteDepth()
                    && rewrites < limits.maximumRewrites() && RewritePositions.rewritable(current, assigned); stacked++) {
                var options = applicable(current, temporal);
                var slots = options.stream().mapToInt(RuleOperands.Plan::weight).sum();
                if (slots == 0 || !draw.drawBoolean()) {
                    break;
                }
                var chosen = RuleOperands.select(options, RuleOperands.Plan::weight, draw.drawIndex(slots, SELECTION_BYTES));
                var replaced = current;
                current = apply(chosen, scope);
                body = body.replace(Extent.of(replaced), Extent.of(current));
                rewrites++;
                applied.add(chosen.rule().name());
            }
            var below = current != node ? new Stack(continued ? stack.origin() : node, stacked) : stack;
            return descend(current, scope, below, temporal);
        }

        private TlaEx descend(TlaEx node, List<OperandGenerator.Name> scope, Stack stack, boolean temporal) {
            return switch (node) {
                case OperEx application -> descendApplication(application, scope, stack, temporal);
                case LetInEx let -> descendLet(let, scope, stack, temporal);
                default -> node;
            };
        }

        private TlaEx descendApplication(
                OperEx application, List<OperandGenerator.Name> scope, Stack stack, boolean temporal) {
            var arguments = TlaExpressions.arguments(application);
            var binding = IrBinding.of(application.oper());
            var inner = new ArrayList<>(scope);
            IrBinding.boundNames(application).forEach(name -> inner.add(
                    new OperandGenerator.Name(name.name(), TlaTypes.typeOf(name), OperandGenerator.Kind.BINDER)));
            var rebuilt = new ArrayList<TlaEx>(arguments.size());
            var changed = false;
            // A label starts a new label scope, as a LET definition does.
            var labelled = application.oper() == TlaOperators.LABEL;
            for (var index = 0; index < arguments.size(); index++) {
                var argument = arguments.get(index);
                var argumentScope = labelled ? newLabelScope(scope)
                        : binding.scopes(index, arguments.size()) ? inner : scope;
                var argumentTemporal = temporal
                        && FormulaLevels.passesFormula(application.oper(), index, arguments.size());
                var visited = RewritePositions.visits(application, index)
                        ? visitArgument(application, index, argumentScope, stack, argumentTemporal)
                        : argument;
                changed |= visited != argument;
                rebuilt.add(visited);
            }
            return changed ? TlaExpressions.withArguments(application, rebuilt) : application;
        }

        private TlaEx visitArgument(
                OperEx application, int index, List<OperandGenerator.Name> scope, Stack stack, boolean temporal) {
            var argument = TlaExpressions.arguments(application).get(index);
            if (!RewritePositions.rewritesArgument(application, index)) {
                return descend(argument, scope, stack, temporal);
            }
            if (!RewritePositions.isExceptReplacement(application, index)) {
                return visit(argument, scope, stack, temporal);
            }
            exceptReplacements++;
            try {
                return visit(argument, scope, stack, temporal);
            } finally {
                exceptReplacements--;
            }
        }

        /**
         * Visits a LET. A declaration's body sees the declarations before it but neither itself nor
         * a later one, since LET definitions are not recursive, and it starts a new label scope: a
         * binder enclosing the LET stays visible there but is no parameter of a label in it.
         */
        private TlaEx descendLet(LetInEx let, List<OperandGenerator.Name> scope, Stack stack, boolean temporal) {
            var declarations = TlaExpressions.localDeclarations(let);
            var inner = new ArrayList<>(scope);
            var preceding = newLabelScope(scope);
            var rebuilt = new ArrayList<TlaOperDecl>(declarations.size());
            var changed = false;
            for (var declaration : declarations) {
                var parameters = new ArrayList<>(preceding);
                TlaDeclarations.parameters(declaration).forEach(parameter -> parameters.add(new OperandGenerator.Name(
                        parameter.name(), parameter.type(), OperandGenerator.Kind.DEFINITION)));
                var body = visit(declaration.body(), parameters, stack, false);
                changed |= body != declaration.body();
                rebuilt.add(body == declaration.body() ? declaration : TlaDeclarations.withBody(declaration, body));
                var defined = new OperandGenerator.Name(
                        declaration.name(), TlaTypes.typeOf(declaration), OperandGenerator.Kind.DEFINITION);
                preceding.add(defined);
                inner.add(defined);
            }
            var body = visit(let.body(), inner, stack, temporal);
            if (!changed && body == let.body()) {
                return let;
            }
            return (TlaEx) TlaExpressions.letIn(body, rebuilt).withTag(let.typeTag());
        }

        /**
         * Returns {@code scope} as seen from a new label scope, the body of a label or of a LET
         * definition: an enclosing binder stays visible but is no parameter of a label in it.
         */
        private static List<OperandGenerator.Name> newLabelScope(List<OperandGenerator.Name> scope) {
            var demoted = new ArrayList<OperandGenerator.Name>(scope.size());
            for (var name : scope) {
                demoted.add(name.kind() == OperandGenerator.Kind.BINDER
                        ? new OperandGenerator.Name(name.name(), name.type(), OperandGenerator.Kind.DEFINITION)
                        : name);
            }
            return demoted;
        }

        /**
         * Returns the rules that match at {@code node}, whose open type variables fit the limits; a
         * rule that raises the level applies only at a temporal position.
         */
        private List<RuleOperands.Plan> applicable(TlaEx node, boolean temporal) {
            var options = new ArrayList<RuleOperands.Plan>();
            for (var rule : rules.candidates(node)) {
                if (rule.weight() == 0 || rule.levels().raisesLevel() && !temporal) {
                    continue;
                }
                RuleMatcher.match(rule, node, assigned).ifPresent(match -> {
                    var original = Extent.of(node);
                    var replacement = estimated(match);
                    var after = body.replace(original, replacement);
                    if (after.nodes() > sizeLimit
                            || replacement.temporal() > original.temporal()
                                    && after.temporal() > limits.maximumTemporalOperators()) {
                        return;
                    }
                    RuleOperands.planFresh(match, operands).ifPresent(options::add);
                });
            }
            return options;
        }

        private TlaEx apply(RuleOperands.Plan option, List<OperandGenerator.Name> scope) {
            var drawn = RuleOperands.draw(option, draw, operands, scope, ignored -> exceptReplacements > 0);
            return RuleInstantiation.instantiate(
                    option.match(), drawn.values(), drawn.types(), RuleOperands.freshNames(operands));
        }

        /**
         * Estimates the extent of a match's replacement before any fresh operand is drawn: a bound
         * parameter counts its binding, and a fresh one a single node.
         */
        private Extent estimated(RuleMatch match) {
            var rule = match.rule();
            var total = new Extent[] {Extent.NONE};
            TlaExpressions.forEach(rule.replacement(), node -> {
                var parameter = node instanceof NameEx name
                        ? rule.parameter(name.name()) : Optional.<RuleParameter>empty();
                total[0] = total[0].plus(parameter.map(value -> value.kind() == RuleParameter.Kind.FRESH
                        ? Extent.NODE : Extent.of(match.bindings().get(value.name()))).orElseGet(() -> Extent.node(node)));
            });
            return total[0];
        }

        private int freshSize() {
            return generation.expressions().maximumNodes();
        }
    }

    /** The nodes of an expression and, among them, its operators of temporal level (ADR 0030). */
    record Extent(int nodes, int temporal) {
        static final Extent NONE = new Extent(0, 0);
        /** A node that is no temporal operator. */
        static final Extent NODE = new Extent(1, 0);

        static Extent of(TlaEx expression) {
            var total = new Extent[] {NONE};
            TlaExpressions.forEach(expression, node -> total[0] = total[0].plus(node(node)));
            return total[0];
        }

        /** The extent of {@code node} alone, without its operands. */
        static Extent node(TlaEx node) {
            return node instanceof OperEx application && FormulaLevels.TEMPORAL.contains(application.oper())
                    ? new Extent(1, 1) : NODE;
        }

        Extent plus(Extent other) {
            return new Extent(nodes + other.nodes, temporal + other.temporal);
        }

        /** This extent with the subexpression {@code removed} replaced by {@code added}. */
        Extent replace(Extent removed, Extent added) {
            return new Extent(nodes - removed.nodes + added.nodes, temporal - removed.temporal + added.temporal);
        }
    }
}
