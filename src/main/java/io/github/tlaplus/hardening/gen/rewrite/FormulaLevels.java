package io.github.tlaplus.hardening.gen.rewrite;

import static org.apalache_mc.tla.jir.TlaOperators.*;

import at.forsyte.apalache.tla.lir.LetInEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.oper.TlaOper;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaExpressions;

/**
 * The levels of formulas, as SANY checks them, that rules depend on (ADR 0017 §5.2, ADR 0027 §1,
 * ir-generators.md §7.1). An action occurs in a temporal formula only as the action of {@code
 * [A]_v}, {@code <<A>>_v}, {@code WF_v(A)}, {@code SF_v(A)} or {@code ENABLED A}; elsewhere, a
 * prime or {@code UNCHANGED} makes a formula an action.
 */
final class FormulaLevels {
    /** Operators of temporal level. */
    static final Set<TlaOper> TEMPORAL = operators(GLOBALLY, EVENTUALLY, LEADS_TO, GUARANTEES,
            WEAK_FAIRNESS, STRONG_FAIRNESS, TEMPORAL_EXISTS, TEMPORAL_FORALL);

    /** Operators that make a formula an action outside an action position. */
    static final Set<TlaOper> ACTION = operators(PRIME, UNCHANGED, COMPOSE, STUTTER, NO_STUTTER);

    private FormulaLevels() {}

    /**
     * Whether argument {@code index} of {@code operator} is an action position: the action of
     * {@code [A]_v}, {@code <<A>>_v}, {@code WF_v(A)}, {@code SF_v(A)} or {@code ENABLED A}.
     */
    static boolean isActionPosition(TlaOper operator, int index) {
        if (operator == STUTTER || operator == NO_STUTTER || operator == ENABLED) {
            return index == 0;
        }
        return (operator == WEAK_FAIRNESS || operator == STRONG_FAIRNESS) && index == 1;
    }

    /**
     * Whether {@code expression} is an action: it applies a prime, {@code UNCHANGED}, {@code
     * \cdot}, {@code [A]_v} or {@code <<A>>_v} other than inside {@code [][A]_v}, {@code <><<A>>_v},
     * fairness or {@code ENABLED}, which are formulas.
     */
    static boolean isAction(TlaEx expression) {
        return switch (expression) {
            case LetInEx let -> isAction(let.body())
                    || TlaExpressions.localDeclarations(let).stream().anyMatch(declaration -> isAction(declaration.body()));
            case OperEx application when application.oper() == ENABLED || isTemporalAction(application) -> false;
            case OperEx application -> ACTION.contains(application.oper())
                    || TlaExpressions.arguments(application).stream().anyMatch(FormulaLevels::isAction);
            default -> false;
        };
    }

    /** Returns an identity set of operators, which the IR compares by identity. */
    static Set<TlaOper> operators(TlaOper... operators) {
        return with(Set.of(), operators);
    }

    /** Returns the identity set of {@code operators} and {@code more}. */
    static Set<TlaOper> with(Set<TlaOper> operators, TlaOper... more) {
        var set = Collections.newSetFromMap(new IdentityHashMap<TlaOper, Boolean>());
        set.addAll(operators);
        set.addAll(List.of(more));
        return Collections.unmodifiableSet(set);
    }

    /**
     * Whether {@code application} is a temporal formula built on an action: {@code [][A]_v}, {@code
     * <><<A>>_v}, {@code WF_v(A)} or {@code SF_v(A)}. TLC evaluates the action apart from the
     * formula around it.
     */
    static boolean isTemporalAction(OperEx application) {
        var operator = application.oper();
        if (operator == WEAK_FAIRNESS || operator == STRONG_FAIRNESS) {
            return true;
        }
        if (operator != GLOBALLY && operator != EVENTUALLY) {
            return false;
        }
        var subscripted = operator == GLOBALLY ? STUTTER : NO_STUTTER;
        return TlaExpressions.arguments(application).getFirst() instanceof OperEx operand && operand.oper() == subscripted;
    }
}
