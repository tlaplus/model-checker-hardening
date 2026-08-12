package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.gen.Draw;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExprGenFactoryDeferralTest {
    @Test
    void constructingAFormGeneratorDoesNotAllocateFreshNames() {
        var context = new GenerationContext(IrGenerationConfig.defaults());
        var typeFactory = new IrTypeGenFactory(context);
        var expressionFactory = new IrExprGenFactory(context, typeFactory);
        var factory = new OtherExprGenFactory(context, typeFactory, expressionFactory);
        var operatorType = new OperatorType(
                List.of(PrimitiveType.BOOL), PrimitiveType.BOOL);

        var generator = factory.lambda(operatorType, 1);

        assertEquals("probe0", context.fresh("probe"));
        context.builder().build(new Draw(new byte[0]).draw(generator));
        assertEquals("probe3", context.fresh("probe"));
    }
}
