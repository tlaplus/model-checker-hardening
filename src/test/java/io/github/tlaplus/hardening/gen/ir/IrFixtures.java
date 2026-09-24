package io.github.tlaplus.hardening.gen.ir;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaType1;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;

/** Small typed expressions over integers and a set of integers {@code S}. */
final class IrFixtures {
    static final TlaTypedScopeUncheckedBuilder B = new TlaTypedScopeUncheckedBuilder();
    static final TlaType1 INT_SET = TlaTypes.set(TlaTypes.INT);

    private IrFixtures() {}

    static TlaEx integer(String name) {
        return B.name(name, TlaTypes.INT);
    }

    static TlaEx set() {
        return B.name("S", INT_SET);
    }

    static TlaEx one() {
        return B.integer(1);
    }

    /** {@code \A bound \in S : bound > limit}. */
    static TlaEx allAbove(String bound, TlaEx limit) {
        return B.forall(integer(bound), set(), B.gt(integer(bound), limit));
    }
}
