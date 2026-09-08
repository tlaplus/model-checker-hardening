package io.github.tlaplus.hardening.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.junit.jupiter.api.Test;

class GeneratedSpecTest {
    private final TlaTypedScopeUncheckedBuilder builder = new TlaTypedScopeUncheckedBuilder();

    @Test
    void effectsAreImmutableOrderedAndHaveSetIdentity() {
        var names = new ArrayList<>(List.of("y", "x"));
        var effect = new ActionEffect(names);
        names.clear();
        assertEquals(List.of("y", "x"), effect.variables());
        assertEquals(new ActionEffect(List.of("x", "y")), effect);
        assertEquals(new ActionEffect(List.of("x", "y")).hashCode(), effect.hashCode());
        assertThrows(UnsupportedOperationException.class, () -> effect.variables().clear());
        assertThrows(IllegalArgumentException.class, () -> new ActionEffect(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ActionEffect(List.of("x", "x")));
    }

    @Test
    void definitionsAndBodiesKeepOneDeclarationOrder() {
        var auxiliary = new GeneratedOperator.Auxiliary(builder.decl("Op0", builder.bool(true)));
        var action = new GeneratedActionOperator(builder.decl("Act1", builder.bool(false)),
                new ActionEffect(List.of("x")));
        var spec = spec(List.of(auxiliary, action));
        assertEquals(List.of(auxiliary.declaration().body(), action.declaration().body()),
                spec.generated().subList(0, 2));
        assertSame(action, spec.operators().get(1));
        assertThrows(UnsupportedOperationException.class, () -> spec.operators().clear());
    }

    @Test
    void undeclaredEffectsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> spec(List.of(
                new GeneratedActionOperator(builder.decl("Act0", builder.bool(true)),
                        new ActionEffect(List.of("missing"))))));
    }

    private GeneratedSpec spec(List<GeneratedOperator> operators) {
        var predicate = builder.bool(true);
        return new GeneratedSpec(List.of(TlaDeclarations.variable("x",
                        at.forsyte.apalache.tla.lir.TlaType1$.MODULE$.fromTypeTag(predicate.typeTag()))),
                operators, predicate, predicate, predicate, predicate, 0);
    }
}
