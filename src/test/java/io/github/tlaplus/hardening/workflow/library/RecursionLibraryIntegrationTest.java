package io.github.tlaplus.hardening.workflow.library;

import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.config.FuzzTlaConfig;
import io.github.tlaplus.hardening.config.OperatorLibraryConfig;
import io.github.tlaplus.hardening.config.TomlConfig;
import io.github.tlaplus.hardening.gen.library.LibraryLinkage;
import io.github.tlaplus.hardening.gen.library.ModuleLink;
import io.github.tlaplus.hardening.workflow.spec.FuzzInputModule;
import io.github.tlaplus.hardening.workflow.tlc.TlcCheckerBackend;
import io.github.tlaplus.hardening.workflow.worker.StageOutcome;
import io.github.tlaplus.hardening.workflow.worker.ToolInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Keeps the shipped recursion library valid: it prepares, and TLC finds every pair equal. */
class RecursionLibraryIntegrationTest {
    private static final Path LIBRARY = Path.of("libraries/recursion.toml");
    private static final Path PAIRS = Path.of("src/test/resources/recursion/RecursionPairs.tla");
    /** The operators whose signatures have type variables; every other one is monomorphic. */
    private static final Set<String> POLYMORPHIC = Set.of(
            "SetCount", "SetUnionAll", "SetPowerset", "SeqReverse", "SeqToSet", "SeqFlatten", "SeqCountOf",
            "SeqEvenLength", "FunSumValues", "FunIncrementAll", "TransitiveClosure", "Reachable");

    @Test
    void everyOperatorIsDiffLinkedAndPassesPreparation() throws Exception {
        var libraries = TomlConfig.readLibrary(LIBRARY);
        var library = LibraryPreparation.prepare(FuzzTlaConfig.defaults().withLibraries(libraries))
                .generator().library();
        assertEquals(libraries.operators().size(), library.exports().size());
        for (var export : library.exports()) {
            assertEquals(new ModuleLink(LibraryLinkage.DIFF, "RecursionTLC"), export.link(), export.id().toString());
            // Snowcat generalizes a parameter that only LET definitions constrain
            // (apalache-typechecker-001), so a missing @type would let the generator pass any
            // value to an integer operator.
            assertEquals(POLYMORPHIC.contains(export.id().operator()), !export.signature().isMono(),
                    export.id() + ": " + export.signature());
        }
    }

    @Test
    void theSelfTestCoversEveryOperator() throws Exception {
        var pairs = Files.readString(PAIRS);
        for (var id : TomlConfig.readLibrary(LIBRARY).operators()) {
            assertTrue(pairs.contains("T!" + id.operator() + "(") && pairs.contains("A!" + id.operator() + "("),
                    id + " is missing from " + PAIRS);
        }
    }

    @Test
    void tlcFindsBothDefinitionsOfEveryOperatorEqual(@TempDir Path directory) throws Exception {
        OperatorLibraryConfig libraries = TomlConfig.readLibrary(LIBRARY);
        // The TLC worker checks FuzzInput.tla against Spec and Inv; the assumptions are the test.
        var text = Files.readString(PAIRS)
                .replace("MODULE RecursionPairs", "MODULE " + FuzzInputModule.MODULE_NAME);
        var backend = new TlcCheckerBackend(new CheckerStageConfig(10, 120, 1024, 1), 1, directory,
                libraries.classpath().stream().map(Path::toAbsolutePath).toList());
        try (var worker = backend.startWorker()) {
            var result = worker.check(new ToolInput(text, 0));
            assertEquals(StageOutcome.PASS, result.outcome(), result.diagnostic());
        }
    }
}
