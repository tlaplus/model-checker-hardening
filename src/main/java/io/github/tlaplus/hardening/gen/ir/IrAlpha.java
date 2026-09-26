package io.github.tlaplus.hardening.gen.ir;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.ValEx;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaExpressions;

/**
 * Alpha-equivalence of typed IR: two expressions are equivalent when they differ only in the
 * names of the variables and definitions they bind. Types and node identities are ignored, as by
 * {@code TlaEx.equals}.
 */
public final class IrAlpha {
    private IrAlpha() {}

    /** Whether {@code left} and {@code right} are equal up to the names they bind. */
    public static boolean equivalent(TlaEx left, TlaEx right) {
        return new Comparison().equivalent(
                Objects.requireNonNull(left, "left"), Objects.requireNonNull(right, "right"), Map.of(), Map.of());
    }

    /** Numbers binders in the order both sides introduce them, so equal numbers mean the same binder. */
    private static final class Comparison {
        private int binders;

        boolean equivalent(TlaEx left, TlaEx right, Map<String, Integer> leftScope, Map<String, Integer> rightScope) {
            return switch (left) {
                case NameEx leftName when right instanceof NameEx rightName ->
                        sameName(leftName.name(), rightName.name(), leftScope, rightScope);
                case ValEx leftValue when right instanceof ValEx rightValue -> leftValue.equals(rightValue);
                case LetInEx leftLet when right instanceof LetInEx rightLet ->
                        equivalentLet(leftLet, rightLet, leftScope, rightScope);
                case OperEx leftApplication when right instanceof OperEx rightApplication ->
                        equivalentApplication(leftApplication, rightApplication, leftScope, rightScope);
                default -> false;
            };
        }

        private boolean equivalentApplication(OperEx left, OperEx right,
                Map<String, Integer> leftScope, Map<String, Integer> rightScope) {
            if (left.oper() != right.oper()) {
                return false;
            }
            var leftArguments = TlaExpressions.arguments(left);
            var rightArguments = TlaExpressions.arguments(right);
            if (leftArguments.size() != rightArguments.size()) {
                return false;
            }
            var binding = IrBinding.of(left.oper());
            var leftInner = new HashMap<>(leftScope);
            var rightInner = new HashMap<>(rightScope);
            for (var index = 0; index < leftArguments.size(); index++) {
                if (binding.introduces(index)
                        && !bind(IrBinding.names(leftArguments.get(index)),
                                IrBinding.names(rightArguments.get(index)), leftInner, rightInner)) {
                    return false;
                }
            }
            for (var index = 0; index < leftArguments.size(); index++) {
                if (binding.introduces(index)) {
                    continue;
                }
                var inScope = binding.scopes(index, leftArguments.size());
                if (!equivalent(leftArguments.get(index), rightArguments.get(index),
                        inScope ? leftInner : leftScope, inScope ? rightInner : rightScope)) {
                    return false;
                }
            }
            return true;
        }

        private boolean equivalentLet(LetInEx left, LetInEx right,
                Map<String, Integer> leftScope, Map<String, Integer> rightScope) {
            var leftDeclarations = TlaExpressions.localDeclarations(left);
            var rightDeclarations = TlaExpressions.localDeclarations(right);
            if (leftDeclarations.size() != rightDeclarations.size()) {
                return false;
            }
            var leftInner = new HashMap<>(leftScope);
            var rightInner = new HashMap<>(rightScope);
            for (var index = 0; index < leftDeclarations.size(); index++) {
                bind(List.of(leftDeclarations.get(index).name()), List.of(rightDeclarations.get(index).name()),
                        leftInner, rightInner);
            }
            for (var index = 0; index < leftDeclarations.size(); index++) {
                var leftDeclaration = leftDeclarations.get(index);
                var rightDeclaration = rightDeclarations.get(index);
                var leftParameters = TlaDeclarations.parameters(leftDeclaration);
                var rightParameters = TlaDeclarations.parameters(rightDeclaration);
                if (leftParameters.size() != rightParameters.size()) {
                    return false;
                }
                var leftBody = new HashMap<>(leftInner);
                var rightBody = new HashMap<>(rightInner);
                for (var parameter = 0; parameter < leftParameters.size(); parameter++) {
                    bind(List.of(leftParameters.get(parameter).name()), List.of(rightParameters.get(parameter).name()),
                            leftBody, rightBody);
                }
                if (!equivalent(leftDeclaration.body(), rightDeclaration.body(), leftBody, rightBody)) {
                    return false;
                }
            }
            return equivalent(left.body(), right.body(), leftInner, rightInner);
        }

        /** Binds each pair of names to one fresh number; the patterns must have the same shape. */
        private boolean bind(List<?> left, List<?> right, Map<String, Integer> leftScope, Map<String, Integer> rightScope) {
            if (left.size() != right.size()) {
                return false;
            }
            for (var index = 0; index < left.size(); index++) {
                var number = binders++;
                leftScope.put(spelling(left.get(index)), number);
                rightScope.put(spelling(right.get(index)), number);
            }
            return true;
        }

        private static String spelling(Object name) {
            return name instanceof NameEx expression ? expression.name() : (String) name;
        }

        private static boolean sameName(String left, String right,
                Map<String, Integer> leftScope, Map<String, Integer> rightScope) {
            var leftBinder = leftScope.get(left);
            var rightBinder = rightScope.get(right);
            if (leftBinder == null || rightBinder == null) {
                return leftBinder == null && rightBinder == null && left.equals(right);
            }
            return leftBinder.equals(rightBinder);
        }
    }
}
