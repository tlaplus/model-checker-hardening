package io.github.tlaplus.hardening.workflow.spec;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import at.forsyte.apalache.tla.lir.IntT1$;
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
    void rendersLabelParametersAsIdentifiersRatherThanStringLiterals() {
        // The IR carries a label's name and formal parameters as string arguments. TLA+ wants
        // them as identifiers, so a writer that passed them through verbatim would emit
        // lab("y") :: ... , which SANY rejects.
        var builder = new TlaTypedScopeUncheckedBuilder();
        var bound = builder.name("y", IntT1$.MODULE$);
        var labeled = builder.label(builder.eql(bound, builder.integer(1)), "lab", "y");
        var module = FuzzInputModule.create(
                builder.forall(bound, builder.enumSet(builder.integer(1)), labeled));

        assertTrue(SpecText.render(module).contains("lab(y) :: (y = 1)"), SpecText.render(module));
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
