package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import at.forsyte.apalache.tla.lir.BoolT1$;
import at.forsyte.apalache.tla.lir.TlaType1$;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import org.junit.jupiter.api.Test;

class GenerationContextTest {
    @Test
    void scopeUncheckedBuilderConstructsDeepExpressionsEagerly() {
        var builder = new GenerationContext(IrGenerationConfig.defaults()).builder();
        var expression = builder.bool(false);

        for (var index = 0; index < 10_000; index++) {
            expression = builder.not(expression);
        }

        assertEquals(
                BoolT1$.MODULE$,
                TlaType1$.MODULE$.fromTypeTag(expression.typeTag()));
    }
}
