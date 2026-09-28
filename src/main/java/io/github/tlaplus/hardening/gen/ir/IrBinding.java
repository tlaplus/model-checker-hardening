package io.github.tlaplus.hardening.gen.ir;

import static org.apalache_mc.tla.jir.TlaOperators.*;

import at.forsyte.apalache.tla.lir.NameEx;
import at.forsyte.apalache.tla.lir.OperEx;
import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.oper.TlaOper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaOperators;

/**
 * Where an operator binds names: which of its arguments introduce bound names, and which arguments
 * see them. Every walk that tracks lexical scope reads this one table.
 *
 * <p>A bound name is a {@link NameEx}, or a tuple of names for a pattern such as {@code \E <<a, b>>
 * \in S : P}. The temporal quantifiers {@code \EE} and {@code \AA} are not listed: no walk that
 * reads this table visits a temporal formula yet.
 */
public enum IrBinding {
    /** Binds nothing. */
    NONE(),
    /** {@code op(x, S, body)}: {@code x} ranges over {@code S} in {@code body}. */
    SINGLE_BOUNDED(SET_FILTER, FORALL3, EXISTS3, CHOOSE3),
    /** {@code op(x, body)}: {@code x} is bound in {@code body}. */
    SINGLE_UNBOUNDED(FORALL2, EXISTS2, CHOOSE2),
    /** {@code op(body, x1, S1, ..., xn, Sn)}: every {@code xi} is bound in {@code body} only. */
    MULTIPLE(SET_MAP, FUN_CTOR);

    /** The operators that bind names this way; empty for {@link #NONE}, which is the default. */
    private final List<TlaOper> operators;

    private static final Map<TlaOper, IrBinding> BINDERS = cacheBindings();

    IrBinding(TlaOper... operators) {
        this.operators = List.of(operators);
    }

    /** Returns how {@code operator} binds names. */
    public static IrBinding of(TlaOper operator) {
        return BINDERS.getOrDefault(operator, NONE);
    }

    /** Whether argument {@code index} introduces bound names. */
    public boolean introduces(int index) {
        return switch (this) {
            case NONE -> false;
            case SINGLE_BOUNDED, SINGLE_UNBOUNDED -> index == 0;
            // (body, x1, S1, ..., xn, Sn): the names x1, ..., xn sit at the odd indices
            case MULTIPLE -> index % 2 == 1;
        };
    }

    /** Whether argument {@code index} of an application with {@code arity} arguments sees the bound names. */
    public boolean scopes(int index, int arity) {
        return switch (this) {
            case NONE -> false;
            case SINGLE_BOUNDED, SINGLE_UNBOUNDED -> index == arity - 1;
            // (body, x1, S1, ..., xn, Sn): only the body sees x1, ..., xn; the domains Si do not
            case MULTIPLE -> index == 0;
        };
    }

    /** Returns every name the arguments of {@code application} bind, in argument order. */
    public static List<NameEx> boundNames(OperEx application) {
        var binding = of(application.oper());
        var arguments = TlaExpressions.arguments(application);
        var names = new ArrayList<NameEx>();
        for (var index = 0; index < arguments.size(); index++) {
            if (binding.introduces(index)) {
                names.addAll(names(arguments.get(index)));
            }
        }
        return List.copyOf(names);
    }

    /**
     * Whether two binder patterns have the same structure, ignoring names and type annotations.
     * Names match names; tuples match recursively in component order. Unsupported structures
     * return {@code false}.
     */
    public static boolean sameShape(TlaEx left, TlaEx right) {
        if (left instanceof NameEx && right instanceof NameEx) {
            return true;
        }
        if (!(left instanceof OperEx leftTuple) || leftTuple.oper() != TUPLE
                || !(right instanceof OperEx rightTuple) || rightTuple.oper() != TUPLE) {
            return false;
        }
        var leftElements = TlaExpressions.arguments(leftTuple);
        var rightElements = TlaExpressions.arguments(rightTuple);
        if (leftElements.size() != rightElements.size()) {
            return false;
        }
        for (var index = 0; index < leftElements.size(); index++) {
            if (!sameShape(leftElements.get(index), rightElements.get(index))) {
                return false;
            }
        }
        return true;
    }

    /** Returns the names one binder argument introduces: one name, or the names of a tuple pattern. */
    public static List<NameEx> names(TlaEx binder) {
        if (binder instanceof NameEx name) {
            return List.of(name);
        }
        if (binder instanceof OperEx tuple && tuple.oper() == TlaOperators.TUPLE) {
            var names = new ArrayList<NameEx>();
            TlaExpressions.arguments(tuple).forEach(element -> names.addAll(names(element)));
            return List.copyOf(names);
        }
        throw new IllegalArgumentException("unsupported binding: " + binder);
    }

    private static Map<TlaOper, IrBinding> cacheBindings() {
        var result = new IdentityHashMap<TlaOper, IrBinding>();
        for (var binding : values()) {
            for (var operator : binding.operators) {
                var previous = result.put(operator, binding);
                if (previous != null) {
                    throw new IllegalStateException(
                            "operator " + operator.name() + " is listed by both " + previous + " and " + binding);
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
