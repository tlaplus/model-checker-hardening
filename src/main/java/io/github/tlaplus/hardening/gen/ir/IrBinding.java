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
    NONE,
    /** {@code op(x, S, body)}: {@code x} ranges over {@code S} in {@code body}. */
    SINGLE_BOUNDED,
    /** {@code op(x, body)}: {@code x} is bound in {@code body}. */
    SINGLE_UNBOUNDED,
    /** {@code op(body, x1, S1, ..., xn, Sn)}: every {@code xi} is bound in {@code body} only. */
    MULTIPLE;

    private static final Map<TlaOper, IrBinding> BINDERS = index();

    /** Returns how {@code operator} binds names. */
    public static IrBinding of(TlaOper operator) {
        return BINDERS.getOrDefault(operator, NONE);
    }

    /** Whether argument {@code index} introduces bound names. */
    public boolean introduces(int index) {
        return switch (this) {
            case NONE -> false;
            case SINGLE_BOUNDED, SINGLE_UNBOUNDED -> index == 0;
            case MULTIPLE -> index % 2 == 1;
        };
    }

    /** Whether argument {@code index} of an application with {@code arity} arguments sees the bound names. */
    public boolean scopes(int index, int arity) {
        return switch (this) {
            case NONE -> false;
            case SINGLE_BOUNDED, SINGLE_UNBOUNDED -> index == arity - 1;
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

    private static Map<TlaOper, IrBinding> index() {
        var result = new IdentityHashMap<TlaOper, IrBinding>();
        for (var operator : List.of(SET_FILTER, FORALL3, EXISTS3, CHOOSE3)) {
            result.put(operator, SINGLE_BOUNDED);
        }
        for (var operator : List.of(FORALL2, EXISTS2, CHOOSE2)) {
            result.put(operator, SINGLE_UNBOUNDED);
        }
        for (var operator : List.of(SET_MAP, FUN_CTOR)) {
            result.put(operator, MULTIPLE);
        }
        return Collections.unmodifiableMap(result);
    }
}
