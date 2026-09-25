package io.github.tlaplus.hardening.workflow.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaModule;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.TemporalProperty;
import io.github.tlaplus.hardening.signature.KnownDefectDatabase;
import java.util.List;
import java.util.Optional;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

/**
 * Matches the signature databases against the entry points {@link FuzzInputModule} adds to every
 * input. A signature that matches this scaffolding, such as {@code Spec}'s {@code [][Next]_vars},
 * would quarantine every candidate.
 */
class KnownDefectScaffoldingTest {
    private final TlaTypedScopeUncheckedBuilder builder = new TlaTypedScopeUncheckedBuilder();
    private final TlaEx truth = builder.bool(true);

    @Test
    void noSignatureMatchesTheScaffoldingOfAnExpressionModule() throws Exception {
        assertEquals(List.of(), ids(FuzzInputModule.create(truth)));
    }

    @Test
    void noSignatureMatchesTheScaffoldingOfAGeneratedModule() throws Exception {
        assertEquals(List.of(), ids(module(truth)));
    }

    /** The action-bound signature still matches {@code [][A]_v} once it is in {@code Prop}. */
    @Test
    void anActionPropertyInPropIsStillMatched() throws Exception {
        var x = builder.name("x", TlaTypes.BOOL);
        var property = builder.always(builder.stutter(builder.bool(false), x));
        assertEquals(List.of("apalache-always-action-bound"), ids(module(property)));
    }

    private TlaModule module(TlaEx property) {
        var variable = TlaDeclarations.variable("x", TlaTypes.BOOL);
        return FuzzInputModule.create(new GeneratedSpec(List.of(variable), List.of(),
                truth, truth, truth, Optional.of(new TemporalProperty(List.of(), property)), 0));
    }

    private static List<String> ids(TlaModule module) throws Exception {
        var database = KnownDefectDatabase.load(List.of(KnownDefectDatabase.ALL));
        return database.matches(module, FuzzInputModule.ENTRY_POINTS).stream()
                .map(match -> match.defect().id())
                .toList();
    }
}
