package io.github.tlaplus.hardening.gen.rewrite;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaOperDecl;
import at.forsyte.apalache.tla.lir.TlaType1;
import io.github.tlaplus.hardening.gen.Draw;
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
 *   <li>The walk visits each rewritable body in pre-order and tracks its lexical scope.
 *   <li>At a node where some rule applies, one Boolean marker decides whether to rewrite it; an odd
 *       marker is followed by a fixed-width two-byte index over the weighted slots of the rules that
 *       apply, in declaration order, as {@code IrExprGenFactory} selects forms. A node where no rule
 *       applies reads no byte.
 *   <li>The walk re-examines the replacement, so rewrites stack up to {@link
 *       RewriteLimits#maximumRewriteDepth()}, and then continues into it. A copy of the rewritten
 *       node inside its replacement, like the x of x + 0, is the same node and continues its stack.
 *       {@link RewriteLimits#maximumRewrites()} bounds the rewrites of one body.
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

        Walk(Draw draw, OperandGenerator operands) {
            this.draw = Objects.requireNonNull(draw, "draw");
            this.operands = operands;
        }

        /** Rewrites one body under its own budget of rewrites. */
        TlaEx body(TlaEx expression, List<OperandGenerator.Name> scope) {
            rewrites = 0;
            return visit(expression, scope, null);
        }

        private TlaEx visit(TlaEx node, List<OperandGenerator.Name> scope, Stack stack) {
            var continued = stack != null && IrAlpha.equivalent(node, stack.origin());
            var stacked = continued ? stack.depth() : 0;
            var current = node;
            for (; stacked < limits.maximumRewriteDepth()
                    && rewrites < limits.maximumRewrites() && RewritePositions.rewritable(current); stacked++) {
                var options = applicable(current);
                var slots = options.stream().mapToInt(Option::weight).sum();
                if (slots == 0 || !draw.drawBoolean()) {
                    break;
                }
                var chosen = select(options, draw.drawIndex(slots, SELECTION_BYTES));
                current = apply(chosen, scope);
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
                RuleMatcher.match(rule, node).ifPresent(match -> {
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
}
