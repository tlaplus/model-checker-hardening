package io.github.tlaplus.hardening.signature;

import at.forsyte.apalache.tla.lir.oper.TlaOper;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import org.apalache_mc.tla.jir.TlaOperators;

/**
 * The operators a pattern may name, keyed by {@link TlaOper#name()}.
 *
 * <p>That name is also the {@code oper} field of Apalache's IR JSON, so a pattern names an
 * operator exactly as {@code fuzztla print --apalache-ir} prints it. The table is built from the
 * public constants of the Java facade's {@link TlaOperators}, so it grows with the facade.
 */
final class OperatorNames {
    private static final SortedMap<String, TlaOper> BY_NAME = byName();

    private OperatorNames() {}

    static Optional<TlaOper> find(String name) {
        return Optional.ofNullable(BY_NAME.get(name));
    }

    /** Returns every known operator by name, in name order. */
    static SortedMap<String, TlaOper> all() {
        return BY_NAME;
    }

    private static SortedMap<String, TlaOper> byName() {
        var result = new TreeMap<String, TlaOper>();
        for (var field : TlaOperators.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && TlaOper.class.isAssignableFrom(field.getType())) {
                try {
                    var operator = (TlaOper) field.get(null);
                    result.putIfAbsent(operator.name(), operator);
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException("cannot read " + field, exception);
                }
            }
        }
        return Collections.unmodifiableSortedMap(result);
    }
}
