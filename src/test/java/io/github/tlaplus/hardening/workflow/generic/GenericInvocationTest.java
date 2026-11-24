package io.github.tlaplus.hardening.workflow.generic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tlaplus.hardening.config.GenericCheckerConfig;
import io.github.tlaplus.hardening.config.GenericCompatibility;
import io.github.tlaplus.hardening.workflow.worker.CheckKind;
import io.github.tlaplus.hardening.workflow.worker.CheckRequest;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenericInvocationTest {
    private static final Path DIRECTORY = Path.of("/scratch/generic-1");

    @Test
    void expandsThePlaceholdersOfTheRequest() {
        var checker = new GenericCheckerConfig("Tool", GenericCompatibility.APALACHE, Path.of("/bin/tool"),
                List.of("check", "--init={init}", "--next={next}", "--inv={inv}", "--temporal={temporal}",
                        "--length={length}", "--out={dir}/out", "{config}", "{module_name}", "{module}"));
        var invocation = new GenericInvocation(DIRECTORY, new CheckRequest(4, CheckKind.INVARIANT_AND_PROPERTY));

        assertEquals(List.of("/bin/tool", "check", "--init=Init", "--next=Next", "--inv=Inv", "--temporal=Liveness",
                        "--length=5", "--out=/scratch/generic-1/out", "/scratch/generic-1/FuzzInput.cfg", "FuzzInput",
                        "/scratch/generic-1/FuzzInput.tla"),
                invocation.command(checker));
    }

    /** A value the request lacks, such as a property, is an empty argument, not a missing one. */
    @Test
    void expandsAnAbsentValueToAnEmptyArgument() {
        var checker = new GenericCheckerConfig("Tool", GenericCompatibility.TLC, Path.of("/bin/tool"),
                List.of("{temporal}", "{ir}"));
        var invocation = new GenericInvocation(DIRECTORY, CheckRequest.invariant(2));

        assertEquals(List.of("/bin/tool", "", "/scratch/generic-1/FuzzInput.json"), invocation.command(checker));
    }
}
