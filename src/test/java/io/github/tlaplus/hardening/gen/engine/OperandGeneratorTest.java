package io.github.tlaplus.hardening.gen.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.SetT1;
import io.github.tlaplus.hardening.gen.IrGenerationConfig;
import io.github.tlaplus.hardening.gen.ir.IrNames;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.apalache_mc.tla.jir.TlaExpressions;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;

class OperandGeneratorTest {
    private static final List<OperandGenerator.Name> SCOPE = List.of(
            new OperandGenerator.Name("var0", TlaTypes.INT, OperandGenerator.Kind.STATE_VARIABLE),
            new OperandGenerator.Name("q7", TlaTypes.INT, OperandGenerator.Kind.BINDER),
            new OperandGenerator.Name("e", TlaTypes.typeVariable(0), OperandGenerator.Kind.BINDER));

    @Test
    void drawsOperandsOfTheRequestedTypeWithNamesAboveTheModulesNames() {
        var random = new Random(17);
        var sawScopeName = false;
        for (var sample = 0; sample < 200; sample++) {
            var bytes = new byte[64];
            random.nextBytes(bytes);
            var operand = generator().operand(TlaTypes.INT, SCOPE).generate(bytes);
            assertEquals(TlaTypes.INT, TlaTypes.typeOf(operand));
            for (var bound : IrNames.bound(operand)) {
                var suffix = Integer.parseInt(bound.replaceAll("^\\D*", ""));
                assertTrue(suffix >= 8, bound + " reuses a suffix the module has");
            }
            sawScopeName |= IrNames.free(operand).stream().anyMatch(Set.of("var0", "q7")::contains);
        }
        assertTrue(sawScopeName, "no operand read a name in scope");
    }

    @Test
    void isDeterministicAndFallsBackToATerminalOnExhaustedInput() {
        var bytes = new byte[] {3, 1, 4, 1, 5, 9, 2, 6};
        assertEquals(generator().operand(TlaTypes.INT, SCOPE).generate(bytes),
                generator().operand(TlaTypes.INT, SCOPE).generate(bytes));
        var terminal = generator().operand(TlaTypes.INT, SCOPE).generate(new byte[0]);
        assertEquals(TlaTypes.INT, TlaTypes.typeOf(terminal));
        var nodes = new int[1];
        TlaExpressions.forEach(terminal, ignored -> nodes[0]++);
        assertEquals(1, nodes[0]);
    }

    @Test
    void instantiatesTheTypeVariablesOfATemplate() {
        var instantiated = generator().instantiate(TlaTypes.set(TlaTypes.typeVariable(0))).orElseThrow()
                .generate(new byte[] {0, 0, 0});
        assertTrue(TlaTypes.usedVariables(instantiated).isEmpty(), instantiated.toString());
        assertTrue(instantiated instanceof SetT1, instantiated.toString());
    }

    private static OperandGenerator generator() {
        return new OperandGenerator(IrGenerationConfig.defaults(), Set.of("var0", "q7", "step"));
    }
}
