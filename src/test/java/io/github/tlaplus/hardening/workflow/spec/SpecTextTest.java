package io.github.tlaplus.hardening.workflow.spec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.workflow.worker.ToolWorkerProtocol;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.junit.jupiter.api.Test;

class SpecTextTest {
    @Test
    void acceptsAModuleThatFitsOneRequestFrame() {
        var module = FuzzInputModule.create(IrGenerators.expressions().generate(new byte[0]));

        assertTrue(SpecText.withinWorkerProtocolLimit(module));
    }

    @Test
    void rejectsAModuleThatRendersPastOneRequestFrame() {
        // The expression is copied into Init and Inv, so a literal of just over half the frame
        // limit renders past the whole limit.
        var literal = "x".repeat(ToolWorkerProtocol.maximumMessageBytes() / 2 + 64);
        var module = FuzzInputModule.create(new TlaTypedScopeUncheckedBuilder().str(literal));

        assertFalse(SpecText.withinWorkerProtocolLimit(module));
    }
}
