package io.github.tlaplus.hardening.gen.rewrite;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.TlaType1;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.GeneratedActionOperator;
import io.github.tlaplus.hardening.gen.GeneratedOperator;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.Generator;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.engine.OperandGenerator;
import io.github.tlaplus.hardening.gen.ir.IrAlpha;
import io.github.tlaplus.hardening.gen.ir.IrBinding;
import io.github.tlaplus.hardening.gen.ir.IrNames;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaTypeSubstitution;
import org.apalache_mc.tla.jir.TlaTypeUnifier;
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
 *   <li>Exhausted input reads even markers, so the rest of the body stays as it is.
 * </ul>
 *
 * <p>Matching is byte-free. A fresh parameter, and any type variable the match leaves open, is
 * drawn from the rewrite payload through {@link OperandGenerator}.
 */
public final class Rewriter {
    /** Bytes of one rule index, as for an expression form (ADR 0016 §2). */
    static final int SELECTION_BYTES = 2;

    private final RewriteLibrary library;
    private final IrGenerationConfig generation;
    private final RewriteLimits limits;

    public Rewriter(RewriteLibrary library, IrGenerationConfig generation, RewriteLimits limits) {
        this.library = Objects.requireNonNull(library, "library");
        this.generation = Objects.requireNonNull(generation, "generation");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    /** Rewrites one expression, the single body of an {@code expr} input. */
    public Rewrite<TlaEx> rewriteExpression(TlaEx expression, Draw draw) {
        Objects.requireNonNull(expression, "expression");
        var orientation = Orientation.of(draw.drawBoolean());
        var names = new LinkedHashSet<>(IrNames.free(expression));
        names.addAll(IrNames.bound(expression));
        var walk = new Walk(draw, new OperandGenerator(generation, names));
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
     * the next-state action the variables but not {@code step}, and the invariant everything. The
     * property is left as it is: temporal formulas are not rewritten yet.
     */
    public Rewrite<GeneratedSpec> rewriteSpec(GeneratedSpec spec, Draw draw) {
        Objects.requireNonNull(spec, "spec");
        var orientation = Orientation.of(draw.drawBoolean());
        var names = new LinkedHashSet<String>();
        spec.variables().forEach(variable -> names.add(variable.name()));
        spec.operators().forEach(operator -> names.add(operator.declaration().name()));
        for (var body : spec.generated()) {
            names.addAll(IrNames.free(body));
            names.addAll(IrNames.bound(body));
        }
        var walk = new Walk(draw, new OperandGenerator(generation, names));

        var variables = new ArrayList<OperandGenerator.Name>();
        OperandGenerator.Name step = null;
        for (var variable : spec.variables()) {
            var name = new OperandGenerator.Name(variable.name(), TlaTypes.typeOf(variable), OperandGenerator.Kind.STATE_VARIABLE);
            if (variable.name().equals(GeneratedSpec.STEP_VARIABLE)) {
                step = name;
            } else {
                variables.add(name);
            }
        }
        var auxiliaries = new ArrayList<OperandGenerator.Name>();
        var operators = new ArrayList<GeneratedOperator>();
        for (var operator : spec.operators()) {
            var declaration = operator.declaration();
            var scope = new ArrayList<>(auxiliaries);
            if (operator instanceof GeneratedActionOperator) {
                scope.addAll(variables);
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
                auxiliaries.add(new OperandGenerator.Name(
                        declaration.name(), TlaTypes.typeOf(declaration), OperandGenerator.Kind.DEFINITION));
            }
        }
        var actionScope = new ArrayList<>(auxiliaries);
        actionScope.addAll(variables);
        var stateScope = new ArrayList<>(actionScope);
        stateScope.add(Objects.requireNonNull(step, "step"));
        var initialized = new java.util.HashSet<String>();
        spec.variables().forEach(variable -> initialized.add(variable.name()));
        // Init assigns the unprimed variables, so they keep their assigning positions there.
        var init = walk.body(spec.initPredicate(), auxiliaries, initialized);
        var next = walk.body(spec.nextAction(), actionScope);
        var invariant = walk.body(spec.invariant(), stateScope);
        var rewritten = new GeneratedSpec(spec.variables(), operators, init, next, invariant, spec.property(), spec.stepBound());
        return new Rewrite<>(spec, rewritten, orientation, walk.applied);
    }

    /** One rule that applies at a node, with the plan of any type variable its match leaves open. */
    private record Option(RuleMatch match, TlaType1 residual, Optional<Generator<TlaType1>> instantiation) {
        int weight() {
            return match.rule().weight();
        }
    }

    /** The state of one decoding: its cursor, its name and operand supply, and the rules applied. */
    private final class Walk {
        private final Draw draw;
        private final OperandGenerator operands;
        private final List<String> applied = new ArrayList<>();
        private int rewrites;
        private int bodySize;
        private int sizeLimit;
        private Set<String> assigned = Set.of();

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
            this.assigned = assigned;
            rewrites = 0;
            bodySize = size(expression);
            sizeLimit = bodySize * limits.maximumGrowth() + freshSize();
            return visit(expression, scope, null);
        }

        private TlaEx visit(TlaEx node, List<OperandGenerator.Name> scope, Stack stack) {
            var continued = stack != null && IrAlpha.equivalent(node, stack.origin());
            var stacked = continued ? stack.depth() : 0;
            var current = node;
            for (; stacked < limits.maximumRewriteDepth()
                    && rewrites < limits.maximumRewrites() && RewritePositions.rewritable(current, assigned); stacked++) {
                var options = applicable(current);
                var slots = options.stream().mapToInt(Option::weight).sum();
                if (slots == 0 || !draw.drawBoolean()) {
                    break;
                }
                var chosen = select(options, draw.drawIndex(slots, SELECTION_BYTES));
                var replaced = current;
                current = apply(chosen, scope);
                bodySize += size(current) - size(replaced);
                rewrites++;
                applied.add(chosen.match().rule().name());
            }
            var below = current != node ? new Stack(continued ? stack.origin() : node, stacked) : stack;
            return descend(current, scope, below);
        }

        private TlaEx descend(TlaEx node, List<OperandGenerator.Name> scope, Stack stack) {
            return switch (node) {
                case OperEx application -> descendApplication(application, scope, stack);
                case LetInEx let -> descendLet(let, scope, stack);
                default -> node;
            };
        }

        private TlaEx descendApplication(OperEx application, List<OperandGenerator.Name> scope, Stack stack) {
            var arguments = TlaExpressions.arguments(application);
            var binding = IrBinding.of(application.oper());
            var inner = new ArrayList<>(scope);
            IrBinding.boundNames(application).forEach(name -> inner.add(
                    new OperandGenerator.Name(name.name(), TlaTypes.typeOf(name), OperandGenerator.Kind.BINDER)));
            var rebuilt = new ArrayList<TlaEx>(arguments.size());
            var changed = false;
            for (var index = 0; index < arguments.size(); index++) {
                var argument = arguments.get(index);
                var visited = RewritePositions.visits(application, index)
                        ? visit(argument, binding.scopes(index, arguments.size()) ? inner : scope, stack)
                        : argument;
                changed |= visited != argument;
                rebuilt.add(visited);
            }
            return changed ? TlaExpressions.withArguments(application, rebuilt) : application;
        }

        private TlaEx descendLet(LetInEx let, List<OperandGenerator.Name> scope, Stack stack) {
            var declarations = TlaExpressions.localDeclarations(let);
            var inner = new ArrayList<>(scope);
            declarations.forEach(declaration -> inner.add(new OperandGenerator.Name(
                    declaration.name(), TlaTypes.typeOf(declaration), OperandGenerator.Kind.DEFINITION)));
            var rebuilt = new ArrayList<TlaOperDecl>(declarations.size());
            var changed = false;
            for (var declaration : declarations) {
                var parameters = new ArrayList<>(inner);
                TlaDeclarations.parameters(declaration).forEach(parameter -> parameters.add(new OperandGenerator.Name(
                        parameter.name(), parameter.type(), OperandGenerator.Kind.DEFINITION)));
                var body = visit(declaration.body(), parameters, stack);
                changed |= body != declaration.body();
                rebuilt.add(body == declaration.body() ? declaration : TlaDeclarations.withBody(declaration, body));
            }
            var body = visit(let.body(), inner, stack);
            if (!changed && body == let.body()) {
                return let;
            }
            return (TlaEx) TlaExpressions.letIn(body, rebuilt).withTag(let.typeTag());
        }

        /** Returns the rules that match at {@code node}, whose open type variables fit the limits. */
        private List<Option> applicable(TlaEx node) {
            var options = new ArrayList<Option>();
            for (var rule : library.candidates(node)) {
                if (rule.weight() == 0) {
                    continue;
                }
                RuleMatcher.match(rule, node, assigned).ifPresent(match -> {
                    if (bodySize - size(node) + estimatedSize(match) > sizeLimit) {
                        return;
                    }
                    var residual = residual(match);
                    if (residual == null) {
                        options.add(new Option(match, null, Optional.empty()));
                    } else {
                        operands.instantiate(residual).ifPresent(plan -> options.add(new Option(match, residual, Optional.of(plan))));
                    }
                });
            }
            return options;
        }

        private Option select(List<Option> options, int slot) {
            var remaining = slot;
            for (var option : options) {
                if (remaining < option.weight()) {
                    return option;
                }
                remaining -= option.weight();
            }
            throw new IllegalStateException("slot " + slot + " exceeds the applicable weights");
        }

        private TlaEx apply(Option option, List<OperandGenerator.Name> scope) {
            var match = option.match();
            var types = match.types();
            if (option.residual() != null) {
                var concrete = draw.draw(option.instantiation().orElseThrow());
                types = new TlaTypeUnifier(option.residual())
                        .unify(Optional.of(types), option.residual(), concrete)
                        .orElseThrow(() -> new IllegalStateException("instantiation does not unify: " + concrete))
                        .substitution();
            }
            var fresh = new HashMap<String, TlaEx>();
            for (var parameter : match.rule().parameters(RuleParameter.Kind.FRESH)) {
                fresh.put(parameter.name(), draw.draw(operands.operand(types.applyFully(parameter.type()), scope)));
            }
            return RuleInstantiation.instantiate(match, fresh, types,
                    name -> operands.freshName(name.replaceAll("\\d+$", "")));
        }

        /** Bounds the size of a match's replacement, before any fresh operand is drawn. */
        private int estimatedSize(RuleMatch match) {
            var rule = match.rule();
            var total = new int[1];
            TlaExpressions.forEach(rule.replacement(), node -> {
                var parameter = node instanceof at.forsyte.apalache.tla.lir.NameEx name
                        ? rule.parameter(name.name()) : Optional.<RuleParameter>empty();
                total[0] += parameter.map(value -> value.kind() == RuleParameter.Kind.FRESH
                        ? 1 : size(match.bindings().get(value.name()))).orElse(1);
            });
            return total[0];
        }

        private int freshSize() {
            return generation.expressions().maximumNodes();
        }

        /**
         * Returns an operator type over the type variables the match leaves open in the fresh
         * parameters and the replacement, or null when it leaves none.
         */
        private TlaType1 residual(RuleMatch match) {
            var open = new LinkedHashSet<Integer>();
            for (var parameter : match.rule().parameters(RuleParameter.Kind.FRESH)) {
                open.addAll(TlaTypes.usedVariables(match.types().applyFully(parameter.type())));
            }
            TlaExpressions.forEach(match.rule().replacement(),
                    node -> open.addAll(TlaTypes.usedVariables(match.types().applyFully(TlaTypes.typeOf(node)))));
            if (open.isEmpty()) {
                return null;
            }
            return TlaTypes.operator(TlaTypes.BOOL,
                    open.stream().map(TlaTypes::typeVariable).toArray(TlaType1[]::new));
        }
    }

    /** Counts the nodes of an expression. */
    private static int size(TlaEx expression) {
        var count = new int[1];
        TlaExpressions.forEach(expression, ignored -> count[0]++);
        return count[0];
    }
}
