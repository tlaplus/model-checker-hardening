package io.github.tlaplus.hardening.workflow;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import java.util.Objects;

/** Scores the explicit collection literals contained in one TLA+ expression. */
final class CollectionRichness {
    private CollectionRichness() {}

    static double score(TlaEx expression, double nestingBase) {
        Objects.requireNonNull(expression, "expression");
        if (!Double.isFinite(nestingBase) || nestingBase < 1.0) {
            throw new IllegalArgumentException("nestingBase must be finite and at least 1");
        }
        return score(expression, nestingBase, 0);
    }

    private static double score(TlaEx expression, double nestingBase, int collectionLevel) {
        if (expression instanceof OperEx operator) {
            var literalSize = literalSize(operator);
            var isCollection = literalSize >= 0;
            var total = isCollection
                    ? multiplySaturated(
                            literalSize, StrictMath.pow(nestingBase, collectionLevel))
                    : 0.0;
            var childLevel = collectionLevel + (isCollection ? 1 : 0);
            var arguments = operator.args().iterator();
            while (arguments.hasNext()) {
                total = addSaturated(
                        total, score(arguments.next(), nestingBase, childLevel));
            }
            return total;
        }
        if (expression instanceof LetInEx letIn) {
            var total = score(letIn.body(), nestingBase, collectionLevel);
            var declarations = letIn.decls().iterator();
            while (declarations.hasNext()) {
                total = addSaturated(
                        total,
                        score(declarations.next().body(), nestingBase, collectionLevel));
            }
            return total;
        }
        return 0.0;
    }

    private static int literalSize(OperEx operator) {
        return switch (operator.oper().name()) {
            case "SET_ENUM", "TUPLE" -> operator.args().size();
            case "RECORD" -> operator.args().size() / 2;
            default -> -1;
        };
    }

    private static double multiplySaturated(int size, double weight) {
        var result = size * weight;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }

    private static double addSaturated(double left, double right) {
        var result = left + right;
        return Double.isFinite(result) ? result : Double.MAX_VALUE;
    }
}
