package io.github.tlaplus.hardening.gen.engine;

import at.forsyte.apalache.tla.lir.TlaEx;
import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.Generator;
import java.util.ArrayList;
import java.util.List;

/** Recursive, complete action shapes. Visibility is an immutable, explicit dependency. */
final class ActionShapeGenFactory extends AbstractExprGenFactory {
    /** At least one non-step variable must be assigned on every complete execution path. */
    enum AssignmentRequirement { REQUIRED, OPTIONAL }

    /** Declaration-order variables and the independent shape/expression recursion budgets. */
    record Request(List<ScopedName> variables, int depth, int expressionDepth,
                   AssignmentRequirement requirement) {
        Request {
            variables = List.copyOf(variables);
            if (variables.isEmpty()) {
                throw new IllegalArgumentException("an action shape requires variables");
            }
            java.util.Objects.requireNonNull(requirement, "requirement");
        }

        Request descend(List<ScopedName> subset, AssignmentRequirement assignment) {
            return new Request(subset, depth - 1, expressionDepth, assignment);
        }

        Request descend() {
            return descend(variables, requirement);
        }
    }

    /** Fixed decoder order, pinned together with marker consumption by ActionGenFactoryTest. */
    enum ShapeKind { LEAF, CONJUNCTION, DISJUNCTION, ITE, CALL }

    private static final List<ShapeKind> KINDS = List.of(ShapeKind.values());

    ActionShapeGenFactory(GenerationContext context, IrTypeGenFactory types, IrExprGenFactory expressions) {
        super(context, types, expressions);
    }

    /** Geometric Boolean-marker distribution; the last kind needs no terminating marker. */
    static ShapeKind drawShapeKind(Draw draw) {
        for (int index = 0; index < KINDS.size() - 1; index++) {
            if (!draw.drawBoolean()) {
                return KINDS.get(index);
            }
        }
        return KINDS.getLast();
    }

    /**
     * Accounts for the requested effect exactly once. Conjunctions partition the effect and
     * propagate the assignment obligation only down their spine; alternatives propagate it to
     * both branches. Leaves repair an empty required assignment without spending another byte.
     * Callees already satisfy the obligation. Depth, budget and exhaustion fall back to a leaf
     * before reading kind markers; unusable decoded kinds fall back after reading them.
     */
    Generator<List<TlaEx>> shape(Request request, VisibleActionOperators visible) {
        return draw -> {
            if (request.depth() <= 0 || !context.consumeNode() || draw.isEmpty()) {
                return leaf(draw, request);
            }
            return switch (drawShapeKind(draw)) {
                case LEAF -> leaf(draw, request);
                case CONJUNCTION -> request.variables().size() < 2
                        ? leaf(draw, request) : conjunction(draw, request, visible);
                case DISJUNCTION -> {
                    var armA = conjoin(draw.draw(shape(request.descend(), visible)));
                    var armB = conjoin(draw.draw(shape(request.descend(), visible)));
                    yield List.of(builder().or(armA, armB));
                }
                case ITE -> {
                    var predicate = draw.draw(expression(PrimitiveType.BOOL, request.expressionDepth() - 1));
                    var whenTrue = conjoin(draw.draw(shape(request.descend(), visible)));
                    var whenFalse = conjoin(draw.draw(shape(request.descend(), visible)));
                    yield List.of(builder().ite(predicate, whenTrue, whenFalse));
                }
                case CALL -> {
                    var matching = visible.matching(request.variables());
                    yield matching.isEmpty() ? leaf(draw, request)
                            : call(draw, draw.choose(matching), request.expressionDepth());
                }
            };
        };
    }

    private List<TlaEx> call(Draw draw, VisibleActionOperators.Operator operator, int expressionDepth) {
        var arguments = operator.type().arguments().stream()
                .map(type -> draw.draw(expression(type, expressionDepth - 1)))
                .toArray(TlaEx[]::new);
        return List.of(builder().operApply(builder().name(
                operator.generated().declaration().name(), operator.type().toTlaType()), arguments));
    }

    private List<TlaEx> leaf(Draw draw, Request request) {
        var assigned = new ArrayList<ScopedName>();
        var unchanged = new ArrayList<ScopedName>();
        for (var variable : request.variables()) {
            (draw.drawBoolean() ? assigned : unchanged).add(variable);
        }
        if (request.requirement() == AssignmentRequirement.REQUIRED && assigned.isEmpty()) {
            assigned.add(unchanged.removeFirst());
        }
        var conjuncts = new ArrayList<TlaEx>();
        for (var variable : assigned) {
            conjuncts.add(draw.draw(assignment(variable, request.expressionDepth())));
        }
        if (!unchanged.isEmpty()) {
            conjuncts.add(builder().unchanged(unchangedTarget(unchanged)));
        }
        return conjuncts;
    }

    private List<TlaEx> conjunction(Draw draw, Request request, VisibleActionOperators visible) {
        var groupA = new ArrayList<ScopedName>();
        var groupB = new ArrayList<ScopedName>();
        for (var variable : request.variables()) {
            (draw.drawBoolean() ? groupA : groupB).add(variable);
        }
        if (groupA.isEmpty()) {
            groupA.add(groupB.removeFirst());
        } else if (groupB.isEmpty()) {
            groupB.add(groupA.removeLast());
        }
        var conjuncts = new ArrayList<>(draw.draw(shape(
                request.descend(groupA, request.requirement()), visible)));
        conjuncts.addAll(draw.draw(shape(
                request.descend(groupB, AssignmentRequirement.OPTIONAL), visible)));
        return conjuncts;
    }

    /** Wraps a conjunct list without introducing a single-argument conjunction. */
    TlaEx conjoin(List<TlaEx> conjuncts) {
        return conjuncts.size() == 1 ? conjuncts.getFirst()
                : builder().and(BuilderArrays.expressions(conjuncts));
    }

    private Generator<TlaEx> assignment(ScopedName variable, int expressionDepth) {
        return draw -> {
            var type = variable.type();
            if (nondeterministic(draw, type)) {
                return builder().in(builder().prime(nameOf(variable)),
                        draw.draw(expression(new SetType(type), expressionDepth - 1)));
            }
            return builder().primeEq(nameOf(variable), draw.draw(expression(type, expressionDepth - 1)));
        };
    }

    /** Always spends one marker, including when the configuration excludes sets. */
    boolean nondeterministic(Draw draw, IrType type) {
        var chosen = draw.drawBoolean();
        return chosen && typeFactory.isEnabled(new SetType(type));
    }

    private TlaEx unchangedTarget(List<ScopedName> unchanged) {
        if (unchanged.size() == 1) {
            return nameOf(unchanged.getFirst());
        }
        return builder().tuple(BuilderArrays.expressions(unchanged.stream().map(this::nameOf).toList()));
    }

    TlaEx nameOf(ScopedName variable) {
        return builder().name(variable.name(), variable.type().toTlaType());
    }
}
