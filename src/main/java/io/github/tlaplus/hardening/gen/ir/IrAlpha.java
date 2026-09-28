package io.github.tlaplus.hardening.gen.ir;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.ValEx;
import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import java.util.List;
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
                Objects.requireNonNull(left, "left"), Objects.requireNonNull(right, "right"), Scopes.EMPTY);
    }

    /**
     * The binder number of each name in scope on either side. The maps are persistent, so binding
     * a name shares structure with the enclosing scope instead of copying it.
     */
    private record Scopes(Map<String, Integer> left, Map<String, Integer> right) {
        static final Scopes EMPTY = new Scopes(HashMap.empty(), HashMap.empty());

        Scopes bind(String leftName, String rightName, int number) {
            return new Scopes(left.put(leftName, number), right.put(rightName, number));
        }

        /** Both names refer to corresponding binders, or both are free and spelled alike. */
        boolean sameName(String leftName, String rightName) {
            var leftBinder = left.get(leftName);
            var rightBinder = right.get(rightName);
            return leftBinder.equals(rightBinder) && (leftBinder.isDefined() || leftName.equals(rightName));
        }
    }

    /** Numbers binders in the order both sides introduce them, so equal numbers mean the same binder. */
    private static final class Comparison {
        private int binders;

        boolean equivalent(TlaEx left, TlaEx right, Scopes scopes) {
            return switch (left) {
                case NameEx leftName when right instanceof NameEx rightName ->
                        scopes.sameName(leftName.name(), rightName.name());
                case ValEx leftValue when right instanceof ValEx rightValue -> leftValue.equals(rightValue);
                case LetInEx leftLet when right instanceof LetInEx rightLet -> equivalentLet(leftLet, rightLet, scopes);
                case OperEx leftApplication when right instanceof OperEx rightApplication ->
                        equivalentApplication(leftApplication, rightApplication, scopes);
                default -> false;
            };
        }

        private boolean equivalentApplication(OperEx left, OperEx right, Scopes scopes) {
            if (left.oper() != right.oper()) {
                return false;
            }
            var leftArguments = TlaExpressions.arguments(left);
            var rightArguments = TlaExpressions.arguments(right);
            if (leftArguments.size() != rightArguments.size()) {
                return false;
            }
            var binding = IrBinding.of(left.oper());
            var inner = scopes;
            for (var index = 0; index < leftArguments.size(); index++) {
                if (!binding.introduces(index)) {
                    continue;
                }
                var leftNames = IrBinding.names(leftArguments.get(index));
                var rightNames = IrBinding.names(rightArguments.get(index));
                if (leftNames.size() != rightNames.size()) {
                    return false;
                }
                inner = bind(leftNames, rightNames, inner);
            }
            for (var index = 0; index < leftArguments.size(); index++) {
                if (binding.introduces(index)) {
                    continue;
                }
                var inScope = binding.scopes(index, leftArguments.size());
                if (!equivalent(leftArguments.get(index), rightArguments.get(index), inScope ? inner : scopes)) {
                    return false;
                }
            }
            return true;
        }

        private boolean equivalentLet(LetInEx left, LetInEx right, Scopes scopes) {
            var leftDeclarations = TlaExpressions.localDeclarations(left);
            var rightDeclarations = TlaExpressions.localDeclarations(right);
            if (leftDeclarations.size() != rightDeclarations.size()) {
                return false;
            }
            var inner = scopes;
            for (var index = 0; index < leftDeclarations.size(); index++) {
                inner = inner.bind(leftDeclarations.get(index).name(), rightDeclarations.get(index).name(), binders++);
            }
            for (var index = 0; index < leftDeclarations.size(); index++) {
                var leftDeclaration = leftDeclarations.get(index);
                var rightDeclaration = rightDeclarations.get(index);
                var leftParameters = TlaDeclarations.parameters(leftDeclaration);
                var rightParameters = TlaDeclarations.parameters(rightDeclaration);
                if (leftParameters.size() != rightParameters.size()) {
                    return false;
                }
                var body = inner;
                for (var parameter = 0; parameter < leftParameters.size(); parameter++) {
                    body = body.bind(leftParameters.get(parameter).name(), rightParameters.get(parameter).name(),
                            binders++);
                }
                if (!equivalent(leftDeclaration.body(), rightDeclaration.body(), body)) {
                    return false;
                }
            }
            return equivalent(left.body(), right.body(), inner);
        }

        /** Binds each pair of names, which the caller has checked to be equally many, to one fresh number. */
        private Scopes bind(List<?> left, List<?> right, Scopes scopes) {
            var inner = scopes;
            for (var index = 0; index < left.size(); index++) {
                inner = inner.bind(spelling(left.get(index)), spelling(right.get(index)), binders++);
            }
            return inner;
        }

        private static String spelling(Object name) {
            return name instanceof NameEx expression ? expression.name() : (String) name;
        }
    }
}
