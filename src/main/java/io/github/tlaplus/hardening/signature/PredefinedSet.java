package io.github.tlaplus.hardening.signature;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaValue;
import at.forsyte.apalache.tla.lir.ValEx;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;

/**
 * The predefined sets a pattern names by their TLA+ spelling. The IR value of each is taken from
 * the builder, so this enum does not depend on how Apalache represents it.
 */
enum PredefinedSet {
    STRING("STRING", TlaTypedScopeUncheckedBuilder::stringSet),
    INT("Int", TlaTypedScopeUncheckedBuilder::intSet),
    NAT("Nat", TlaTypedScopeUncheckedBuilder::natSet),
    BOOLEAN("BOOLEAN", TlaTypedScopeUncheckedBuilder::booleanSet);

    private final String spelling;
    private final TlaValue value;

    PredefinedSet(String spelling, Function<TlaTypedScopeUncheckedBuilder, TlaEx> literal) {
        this.spelling = spelling;
        this.value = ((ValEx) literal.apply(new TlaTypedScopeUncheckedBuilder())).value();
    }

    /** Returns the set a pattern spells {@code spelling}, if any. */
    static Optional<PredefinedSet> bySpelling(String spelling) {
        return Arrays.stream(values()).filter(set -> set.spelling.equals(spelling)).findFirst();
    }

    String spelling() {
        return spelling;
    }

    TlaValue value() {
        return value;
    }
}
