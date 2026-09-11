package io.github.tlaplus.hardening.workflow.library;

import at.forsyte.apalache.tla.lir.TlaEx;
import at.forsyte.apalache.tla.lir.TlaType1;
import io.github.tlaplus.hardening.config.CheckerStageConfig;
import io.github.tlaplus.hardening.gen.GeneratedSpec;
import io.github.tlaplus.hardening.gen.library.OperatorId;
import io.github.tlaplus.hardening.gen.library.OperatorLibrary;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheCheckerBackend;
import io.github.tlaplus.hardening.workflow.apalache.ApalacheDistribution;
import io.github.tlaplus.hardening.workflow.tool.ToolBackend;
import io.github.tlaplus.hardening.workflow.parser.ParserWorkerMain;
import io.github.tlaplus.hardening.workflow.spec.SpecArtifact;
import io.github.tlaplus.hardening.workflow.spec.SpecText;
import io.github.tlaplus.hardening.workflow.tlc.TlcCheckerBackend;
import io.github.tlaplus.hardening.workflow.worker.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.apalache_mc.tla.jir.NamedType;
import org.apalache_mc.tla.jir.TlaDeclarations;
import org.apalache_mc.tla.jir.TlaTypedScopeUncheckedBuilder;
import org.apalache_mc.tla.jir.TlaTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CustomLibraryIntegrationTest {
    private static final TlaTypedScopeUncheckedBuilder BUILDER = new TlaTypedScopeUncheckedBuilder();

    @Test
    void selfContainedPolymorphicCallsPassAllTools(@TempDir Path directory) throws Exception {
        var config = LibraryPreparationTest.config(List.of(Path.of("src/test/resources/custom")), "PolyOps",
                "Wrapped", "Empty", "ReadValue", "Local", "Init");
        var library = LibraryPreparation.prepare(config).generator().library();
        var integer = BUILDER.integer(1);
        var bool = BUILDER.bool(true);
        var recordType = TlaTypes.rowRecord(new NamedType("value", TlaTypes.INT));
        var record = BUILDER.record(new org.apalache_mc.tla.jir.NamedExpression<>("value", integer));
        var expression = BUILDER.tuple(
                call(library, new OperatorId("PolyOps", "Wrapped"), TlaTypes.INT, integer),
                call(library, new OperatorId("PolyOps", "Wrapped"), TlaTypes.BOOL, bool),
                call(library, new OperatorId("PolyOps", "Empty"), TlaTypes.set(TlaTypes.INT)),
                call(library, new OperatorId("PolyOps", "ReadValue"), TlaTypes.INT, record),
                call(library, new OperatorId("PolyOps", "Local"), TlaTypes.BOOL, BUILDER.bool(false)),
                call(library, new OperatorId("PolyOps", "Init"), TlaTypes.INT, BUILDER.integer(2)));
        assertAllTools(SpecArtifact.fromExpression(expression, library), directory);
    }

    @Test
    void usefulOperatorsSatisfyTheirContractsAcrossTypes(@TempDir Path directory) throws Exception {
        var config = LibraryPreparationTest.config(List.of(Path.of("src/test/resources/custom")),
                "CustomOperatorsChecks", "Check");
        var library = LibraryPreparation.prepare(config).generator().library();
        var invariant = call(library, new OperatorId("CustomOperatorsChecks", "Check"), TlaTypes.BOOL);
        var state = TlaDeclarations.variable("state", TlaTypes.BOOL);
        var spec = new GeneratedSpec(List.of(state), List.of(),
                BUILDER.eql(BUILDER.varDeclAsNameEx(state), BUILDER.bool(true)),
                BUILDER.unchanged(BUILDER.varDeclAsNameEx(state)), invariant, BUILDER.bool(true), 0);
        assertAllTools(SpecArtifact.fromGeneratedSpec(spec, library), directory);
    }

    private static void assertAllTools(SpecArtifact artifact, Path directory) throws Exception {
        var timeout = Duration.ofSeconds(30);
        var parserScratch = Files.createDirectory(directory.resolve("parser"));
        try (var parser = IsolatedWorkerProcess.start(new WorkerSpec(parserScratch, timeout,
                ParserWorkerMain.class, "custom library parser"))) {
            var result = parser.request(new ToolInput(SpecText.render(artifact.module()), 0), timeout);
            assertEquals(StageOutcome.PASS, result.outcome(), result.diagnostic());
        }
        var settings = new CheckerStageConfig(10, 30, 512, 1);
        var backends = List.<ToolBackend>of(
                new TlcCheckerBackend(settings, 1, Files.createDirectory(directory.resolve("tlc"))),
                new ApalacheCheckerBackend(settings, ApalacheDistribution.locate(),
                        Files.createDirectory(directory.resolve("apalache"))));
        for (var backend : backends) {
            try (var worker = backend.startWorker()) {
                var result = worker.check(new ToolInput(backend.renderer().apply(artifact.module()), 0));
                assertEquals(StageOutcome.PASS, result.outcome(), backend.stage().displayName() + ": " + result.diagnostic());
            }
        }
    }

    private static TlaEx call(OperatorLibrary library, OperatorId id, TlaType1 result, TlaEx... args) {
        var types = java.util.Arrays.stream(args)
                .map(TlaTypes::typeOf)
                .toArray(TlaType1[]::new);
        return BUILDER.operApply(BUILDER.name(library.get(id).name(),
                TlaTypes.operator(result, types)), args);
    }
}
