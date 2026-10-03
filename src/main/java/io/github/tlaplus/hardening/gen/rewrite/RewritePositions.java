package io.github.tlaplus.hardening.gen.rewrite;

import static org.apalache_mc.tla.jir.TlaOperators.*;

import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.OperT1;
import at.forsyte.apalache.tla.lir.RecRowT1;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TupT1;
import at.forsyte.apalache.tla.lir.ValEx;
import at.forsyte.apalache.tla.lir.values.TlaStr;
import io.github.tlaplus.hardening.gen.ir.IrBinding;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaTypes;

/**
 * Where the rewriter may apply a rule. A rule is an equivalence, but some positions of the IR are
 * syntax rather than values, and some keep TLC's assignments working:
 *
 * <ul>
 *   <li>a primed name, and everything under it, stays as it is: {@code (x + 0)' = 1} is not an
 *       assignment; in {@code Init}, where TLC assigns unprimed variables, a variable stays as it
 *       is too;
 *   <li>the operand of {@code UNCHANGED} stays a tuple of variables, for the same reason; the
 *       {@code UNCHANGED} itself may be rewritten;
 *   <li>binder names, label names, record field names, variant tags, and the literal index of a
 *       tuple or record access are syntax, which Apalache requires literal;
 *   <li>a string literal is always one of those names or a value no rule can rewrite into anything
 *       the checkers treat differently;
 *   <li>an operator, such as a lambda argument of a fold or the name an application applies, is
 *       not a value: TLA+ has no {@code IF} or equality of operators, so a rule that admits every
 *       type would produce a module SANY rejects. The body of a lambda is a value again.
 * </ul>
 */
final class RewritePositions {
    private RewritePositions() {}

    /** Whether a rule may apply at {@code node} itself, where {@code assigned} names are assigned. */
    static boolean rewritable(TlaEx node, Set<String> assigned) {
        if (node instanceof OperEx application && application.oper() == PRIME
                || node instanceof NameEx name && assigned.contains(name.name())
                || TlaTypes.typeOf(node) instanceof OperT1) {
            return false;
        }
        return !(node instanceof ValEx value && value.value() instanceof TlaStr);
    }

    /** Whether the rewriter may visit argument {@code index} of {@code application} at all. */
    static boolean visits(OperEx application, int index) {
        var operator = application.oper();
        if (IrBinding.of(operator).introduces(index) || operator == PRIME || operator == UNCHANGED) {
            return false;
        }
        if (operator == LABEL) {
            return index == 0;
        }
        if (operator == RECORD || operator == RECORD_SET) {
            return index % 2 == 1;
        }
        if (operator == VARIANT || operator == VARIANT_FILTER || operator == VARIANT_GET_UNSAFE
                || operator == VARIANT_GET_OR_ELSE) {
            return index != 0;
        }
        if (operator == EXCEPT) {
            return index == 0 || isExceptReplacement(application, index);
        }
        if (operator == FUN_APP && index == 1) {
            var function = TlaTypes.typeOf(TlaExpressions.arguments(application).getFirst());
            return !(function instanceof TupT1) && !(function instanceof RecRowT1);
        }
        return true;
    }

    /**
     * Whether argument {@code index} of {@code application} is an {@code EXCEPT} replacement, where
     * SANY rejects every label: {@code EXCEPT(f, accessor1, value1, ...)} has its values at the even
     * indexes after the function.
     */
    static boolean isExceptReplacement(OperEx application, int index) {
        return application.oper() == EXCEPT && index > 0 && index % 2 == 0;
    }
}
