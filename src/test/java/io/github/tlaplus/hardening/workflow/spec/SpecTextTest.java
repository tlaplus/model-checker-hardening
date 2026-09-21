package io.github.tlaplus.hardening.workflow.spec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tlaplus.hardening.gen.IrGenerators;
import io.github.tlaplus.hardening.gen.library.InstanceAlias;
import io.github.tlaplus.hardening.gen.library.OperatorId;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.gen.library.SourceLink;
import java.util.List;
import io.github.tlaplus.hardening.workflow.worker.ToolWorkerProtocol;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
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
        var bound = builder.name("y", TlaTypes.INT);
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

    @Test
    void definesInstanceAliasesDirectlyAfterExtends() {
        var module = FuzzInputModule.create(new TlaTypedScopeUncheckedBuilder().bool(true));
        var instance = OperatorLibrary.instanceName("Mods");
        var aliases = List.of(
                new InstanceAlias("CustomA", new OperatorId("Mods", "Pair"), 2),
                new InstanceAlias("CustomB", new OperatorId("Mods", "Empty"), 0));
        var plain = SpecText.render(module);
        var text = SpecText.render(new SourceLink(module, aliases));

        var extendsEnd = plain.indexOf("\n\n", plain.indexOf("EXTENDS ")) + 1;
        assertEquals(plain.substring(0, extendsEnd) + "\n"
                + instance + " == INSTANCE Mods\n"
                + "CustomA(CustomP1, CustomP2) == " + instance + "!Pair(CustomP1, CustomP2)\n"
                + "CustomB == " + instance + "!Empty\n"
                + plain.substring(extendsEnd), text);
        assertEquals(plain, SpecText.render(new SourceLink(module, List.of())));
    }
}
